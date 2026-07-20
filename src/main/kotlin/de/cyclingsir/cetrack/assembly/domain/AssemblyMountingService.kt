package de.cyclingsir.cetrack.assembly.domain

import de.cyclingsir.cetrack.assembly.storage.AssemblyDomain2StorageMapper
import de.cyclingsir.cetrack.assembly.storage.AssemblyMembershipEntity
import de.cyclingsir.cetrack.assembly.storage.AssemblyMembershipRepository
import de.cyclingsir.cetrack.assembly.storage.AssemblyMountingEntity
import de.cyclingsir.cetrack.assembly.storage.AssemblyMountingRepository
import de.cyclingsir.cetrack.assembly.storage.AssemblyRepository
import de.cyclingsir.cetrack.assembly.storage.AssemblySlotRepository
import de.cyclingsir.cetrack.assembly.storage.ComponentAssemblyEntity
import de.cyclingsir.cetrack.bike.domain.DomainSlotMapping
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.bike.storage.MountPointRepository
import de.cyclingsir.cetrack.bike.storage.SlotMappingEntity
import de.cyclingsir.cetrack.bike.storage.SlotMappingRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.storage.ComponentRepository
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipAction
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipChange
import de.cyclingsir.cetrack.mounting.domain.DomainMounting
import de.cyclingsir.cetrack.mounting.domain.DomainMountingChanges
import de.cyclingsir.cetrack.mounting.storage.MountingEntity
import de.cyclingsir.cetrack.mounting.storage.MountingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The one place that creates/closes governed Mountings (CE-0086 ruling W3):
 * planMount/mountAssembly/dismountAssembly and the member-mounting side
 * effects of addMember/removeMember. AssemblyService stays CRUD + slots +
 * unmounted membership reads only.
 */
@Service
class AssemblyMountingService(
    private val assemblyRepository: AssemblyRepository,
    private val slotRepository: AssemblySlotRepository,
    private val membershipRepository: AssemblyMembershipRepository,
    private val assemblyMountingRepository: AssemblyMountingRepository,
    private val mountingRepository: MountingRepository,
    private val mountPointRepository: MountPointRepository,
    private val bikeRepository: BikeRepository,
    private val componentRepository: ComponentRepository,
    private val slotMappingRepository: SlotMappingRepository,
    private val mapper: AssemblyDomain2StorageMapper,
) {

    @Transactional(readOnly = true)
    fun planMount(assemblyId: UUID, bikeId: UUID, at: Instant): DomainMountPlan {
        val assembly = requireAssembly(assemblyId)
        requireBike(bikeId)
        val activeSlots = slotRepository.findActiveWithMemberAtTime(assemblyId, at)

        val planned = activeSlots.map { slot ->
            val componentId = slot.memberComponentId
            if (componentId == null) {
                DomainPlannedSlot(slotId = slot.slotId, state = DomainPlannedSlotState.EMPTY)
            } else {
                planSlot(slot.slotId, componentId, slot.componentTypeId, assembly.positionId, bikeId, at, userAnswer = null)
            }
        }
        val mountable = planned.isNotEmpty() && planned.all { it.state == DomainPlannedSlotState.RESOLVED }
        val assembliesToDismount = planned.mapNotNull { it.willDismountAssemblyMountingId }.distinct().map { mountingId ->
            val mounting = assemblyMountingRepository.findById(mountingId).orElseThrow()
            val name = assemblyRepository.findById(mounting.assemblyId).orElseThrow().name
            DomainAssemblyToDismount(assemblyId = mounting.assemblyId, assemblyMountingId = mountingId, name = name)
        }
        return DomainMountPlan(
            assemblyId = assemblyId, bikeId = bikeId, at = at, mountable = mountable, slots = planned,
            assembliesToDismount = assembliesToDismount
        )
    }

    @Transactional
    fun mountAssembly(
        assemblyId: UUID,
        bikeId: UUID,
        at: Instant,
        slotResolutions: List<DomainSlotResolution>,
    ): DomainAssemblyMountResult {
        val assembly = requireAssembly(assemblyId)
        val bike = bikeRepository.findById(bikeId).orElseThrow { ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND) }
        if (bike.retiredAt != null) {
            throw ServiceException(ErrorCodesDomain.BIKE_RETIRED)
        }
        if (assemblyMountingRepository.findByAssemblyIdAndDismountedAtIsNull(assemblyId) != null) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_ALREADY_MOUNTED)
        }
        val activeSlots = slotRepository.findActiveWithMemberAtTime(assemblyId, at)
        if (activeSlots.isEmpty() || activeSlots.any { it.memberComponentId == null }) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_INCOMPLETE)
        }
        val answers = slotResolutions.associate { it.slotId to it.mountPointId }

        val planned = activeSlots.map { slot ->
            planSlot(
                slot.slotId, slot.memberComponentId!!, slot.componentTypeId, assembly.positionId, bikeId, at,
                userAnswer = answers[slot.slotId]
            )
        }

        val unmountable = planned.filter { it.state == DomainPlannedSlotState.IMPOSSIBLE }
        if (unmountable.any { it.reasonCode == DomainImpossibleReason.NO_CANDIDATE || it.reasonCode == DomainImpossibleReason.POSITION_FILTER_EMPTY }) {
            throw ServiceException(
                ErrorCodesDomain.SLOT_UNMOUNTABLE, null,
                mapOf("slots" to unmountable.map { mapOf("slotId" to it.slotId, "reasonCode" to it.reasonCode!!.name) })
            )
        }
        val unresolved = planned.filter { it.state == DomainPlannedSlotState.UNRESOLVED }
        if (unresolved.isNotEmpty()) {
            throw ServiceException(
                ErrorCodesDomain.UNRESOLVED_SLOTS, null,
                mapOf("slots" to unresolved.map { mapOf("slotId" to it.slotId, "candidates" to it.candidates) })
            )
        }
        val memberMountedElsewhere = unmountable.filter { it.reasonCode == DomainImpossibleReason.MEMBER_MOUNTED_ELSEWHERE }
        if (memberMountedElsewhere.isNotEmpty()) {
            throw ServiceException(ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE)
        }
        val resolvedMountPointBySlot = planned.associate { it.slotId to it.mountPointId!! }
        val colliding = SlotResolver.collidingSlotIds(resolvedMountPointBySlot)
        if (colliding.isNotEmpty()) {
            throw ServiceException(ErrorCodesDomain.SLOT_TARGET_COLLISION, null, mapOf("slotIds" to colliding))
        }

        // backdate pre-check before any mutation - direct (non-governed) occupants this mount would evict;
        // governed occupants are covered by validateAssemblyMountingClosable below
        planned.forEach { slot ->
            val occupant = mountingRepository.findByMountPointIdActiveAt(slot.mountPointId!!, at)
            if (occupant != null && occupant.componentId != slot.componentId && occupant.assemblyMountingId == null
                && !at.isAfter(occupant.mountedAt)) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED,
                    "Occupant mounting to close started at ${occupant.mountedAt}.")
            }
        }

        // override: dismount every blocking assembly as a unit, fully validated before any of them mutate.
        // A blocker already closed (occupant found closed-but-containing-T, CE-0120) is excluded -
        // there's nothing to close, and touching it would corrupt its historical dismountedAt;
        // the insert below collides with its untouched governed row on GiST instead.
        val blockingAssemblyMountings = planned.mapNotNull { it.willDismountAssemblyMountingId }.distinct()
            .map { assemblyMountingRepository.findById(it).orElseThrow() }
            .filter { it.dismountedAt == null }
        blockingAssemblyMountings.forEach { validateAssemblyMountingClosable(it, at) }
        val dismountChanges = blockingAssemblyMountings.map { closeAssemblyMounting(it, at) }

        val assemblyMounting = assemblyMountingRepository.saveAndFlush(
            AssemblyMountingEntity(id = null, assemblyId = assemblyId, bikeId = bikeId, mountedAt = at)
        )
        val assemblyMountingId = assemblyMounting.id!!

        val closed = mutableListOf<MountingEntity>()
        val created = mutableListOf<MountingEntity>()
        val toClose = mutableListOf<MountingEntity>()
        val remembered = mutableListOf<SlotMappingEntity>()

        planned.forEach { slot ->
            val mountPointId = slot.mountPointId!!
            val componentId = slot.componentId!!
            val ownMounting = mountingRepository.findByComponentIdActiveAt(componentId, at)
            if (ownMounting != null && ownMounting.dismountedAt == null && ownMounting.mountPointId == mountPointId) {
                ownMounting.assemblyMountingId = assemblyMountingId
                ownMounting.adopted = true
                toClose.add(ownMounting) // reused as the "touch" list, saved below
            } else {
                // an open governed occupant was already closed above via closeAssemblyMounting and
                // is no longer active at T, so it won't be found here - only a direct occupant is
                // ever evicted in this branch. A closed-governed occupant (blocker excluded above)
                // is left untouched; the insert below collides with it on GiST instead.
                val occupant = mountingRepository.findByMountPointIdActiveAt(mountPointId, at)
                if (occupant != null && occupant.assemblyMountingId == null) {
                    occupant.dismountedAt = at
                    toClose.add(occupant)
                    closed.add(occupant)
                }
                created.add(MountingEntity(id = null, componentId = componentId, mountPointId = mountPointId,
                    assemblyMountingId = assemblyMountingId, mountedAt = at))
            }
            // ask-once: remember a freshly-answered resolution not already stored
            val existingMapping = slotMappingRepository.findByAssemblySlotIdAndBikeId(slot.slotId, bikeId)
            if (existingMapping == null && answers[slot.slotId] == mountPointId) {
                remembered.add(SlotMappingEntity(id = null, assemblySlotId = slot.slotId, bikeId = bikeId, mountPointId = mountPointId))
            }
        }

        try {
            // flush closings/adoptions first: exclusion constraints are checked per statement
            mountingRepository.saveAllAndFlush(toClose)
            mountingRepository.saveAllAndFlush(created)
            slotMappingRepository.saveAllAndFlush(remembered)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP, null, e)
        }

        val mountPointNames = planned.associate { it.mountPointId!! to mountPointName(it.mountPointId, bikeId) }
        val changes = DomainMountingChanges(
            created = created.map { toDomainMounting(it, bikeId, mountPointNames[it.mountPointId]!!) },
            closed = dismountChanges.flatMap { it.closed } +
                closed.map { toDomainMounting(it, bikeId, mountPointNames[it.mountPointId]!!) }
        )
        return DomainAssemblyMountResult(
            assemblyMounting = mapper.map(assemblyMounting),
            changes = changes,
            rememberedSlotMappings = remembered.map {
                DomainSlotMapping(it.id, it.assemblySlotId, it.bikeId, it.mountPointId, it.createdAt)
            },
            dismountedAssemblyMountings = blockingAssemblyMountings.map { mapper.map(it) }
        )
    }

    @Transactional
    fun dismountAssembly(assemblyId: UUID, at: Instant): DomainAssemblyMountResult {
        requireAssembly(assemblyId)
        val active = assemblyMountingRepository.findByAssemblyIdAndDismountedAtIsNull(assemblyId)
            ?: throw ServiceException(ErrorCodesDomain.ASSEMBLY_NOT_MOUNTED)
        validateAssemblyMountingClosable(active, at)
        val changes = closeAssemblyMounting(active, at)
        return DomainAssemblyMountResult(assemblyMounting = mapper.map(active), changes = changes)
    }

    /** Backdate checks only, no mutation - callers validate every blocker before mutating any of them. */
    private fun validateAssemblyMountingClosable(active: AssemblyMountingEntity, at: Instant) {
        if (!at.isAfter(active.mountedAt)) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Assembly mounting started at ${active.mountedAt}.")
        }
        val governed = mountingRepository.findAllByAssemblyMountingIdAndDismountedAtIsNull(active.id!!)
        governed.forEach { mounting ->
            if (!at.isAfter(mounting.mountedAt)) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Mounting to close started at ${mounting.mountedAt}.")
            }
        }
    }

    /** Closes the assembly mounting and everything it governs at `at`. Assumes validateAssemblyMountingClosable already passed. */
    private fun closeAssemblyMounting(active: AssemblyMountingEntity, at: Instant): DomainMountingChanges {
        val governed = mountingRepository.findAllByAssemblyMountingIdAndDismountedAtIsNull(active.id!!)
        governed.forEach { it.dismountedAt = at }
        active.dismountedAt = at
        mountingRepository.saveAllAndFlush(governed)
        assemblyMountingRepository.saveAndFlush(active)

        return DomainMountingChanges(
            closed = governed.map { toDomainMounting(it, active.bikeId, mountPointName(it.mountPointId, active.bikeId)) }
        )
    }

    @Transactional
    fun addMember(assemblyId: UUID, componentId: UUID, slotId: UUID, from: Instant, mountPointId: UUID?): DomainMountingChanges {
        val assembly = requireAssembly(assemblyId)
        val slot = slotRepository.findByIdAndAssemblyId(slotId, assemblyId)
            ?: throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_NOT_FOUND)
        val component = componentRepository.findById(componentId)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND) }
        if (component.retiredAt != null) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_RETIRED, "Retired components can't join an assembly.")
        }
        if (component.componentTypeId != slot.componentTypeId) {
            throw ServiceException(ErrorCodesDomain.TYPE_MISMATCH)
        }
        if (membershipRepository.findByComponentIdAndMemberToIsNull(componentId) != null) {
            throw ServiceException(ErrorCodesDomain.ALREADY_MEMBER)
        }
        // Occupied slot -> atomic swap: close the occupant (+ its governed mounting) before adding the new member,
        // parity with bike-side mountComponent auto-dismount. Ordering is load-bearing (see class doc / A4 plan).
        val occupant = membershipRepository.findByAssemblySlotIdAndMemberToIsNull(slotId)
        val swapChanges = if (occupant != null) removeMember(occupant.componentId, from) else DomainMountingChanges()

        val activeAssemblyMounting = assemblyMountingRepository.findByAssemblyIdAndDismountedAtIsNull(assemblyId)
        val branch = if (activeAssemblyMounting == null) {
            if (mountPointId != null) {
                throw ServiceException(ErrorCodesDomain.ASSEMBLY_DATA_INVALID,
                    "mountPointId is not allowed while the assembly is not mounted.")
            }
            // A directly-mounted component MAY join a not-yet-mounted assembly (CE-0106):
            // membership is a composition fact, not a location fact - the component stays
            // directly mounted until the assembly itself is mounted (adoption or conflict
            // then applies, see mountMemberIntoGovernedSlot / planSlot).
            DomainMountingChanges()
        } else {
            mountMemberIntoGovernedSlot(assembly, slot.componentTypeId, slotId, componentId,
                activeAssemblyMounting, from, mountPointId)
        }

        membershipRepository.saveAndFlush(
            AssemblyMembershipEntity(id = null, componentId = componentId, assemblySlotId = slotId, memberFrom = from)
        )
        val membershipChanges = listOfNotNull(
            occupant?.let { DomainMembershipChange(it.componentId, slotId, DomainMembershipAction.REMOVED, from) }
        ) + DomainMembershipChange(componentId, slotId, DomainMembershipAction.ADDED, from)
        return DomainMountingChanges(
            created = branch.created,
            closed = swapChanges.closed + branch.closed,
            membershipChanges = membershipChanges,
        )
    }

    @Transactional
    fun removeMember(componentId: UUID, to: Instant): DomainMountingChanges {
        val membership = membershipRepository.findByComponentIdAndMemberToIsNull(componentId)
            ?: throw ServiceException(ErrorCodesDomain.MEMBERSHIP_NOT_FOUND)
        if (!to.isAfter(membership.memberFrom)) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Membership started at ${membership.memberFrom}.")
        }
        val ownMounting = mountingRepository.findByComponentIdAndDismountedAtIsNull(componentId)
        val changes = if (ownMounting != null && ownMounting.assemblyMountingId != null) {
            if (!to.isAfter(ownMounting.mountedAt)) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Mounting started at ${ownMounting.mountedAt}.")
            }
            ownMounting.dismountedAt = to
            mountingRepository.saveAndFlush(ownMounting)
            val bikeId = mountPointRepository.findById(ownMounting.mountPointId).orElseThrow().bikeId
            DomainMountingChanges(closed = listOf(toDomainMounting(ownMounting, bikeId, mountPointName(ownMounting.mountPointId, bikeId))))
        } else {
            DomainMountingChanges()
        }
        membership.memberTo = to
        membershipRepository.saveAndFlush(membership)
        return changes
    }

    /**
     * Administrative correction of a memberFrom/memberTo data-entry error.
     * Cascades per-boundary to the governed mounting it produced (cascade
     * rule, CE-0108): a boundary is only touched when it coincided with the
     * old value - the assembly-mount time and an adopted direct mounting's
     * start are left untouched.
     */
    @Transactional
    fun correctMembership(
        membershipId: UUID,
        memberFrom: Instant?,
        memberTo: Instant?,
        reopen: Boolean = false,
    ): DomainAssemblyMembership {
        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { ServiceException(ErrorCodesDomain.MEMBERSHIP_NOT_FOUND) }
        if (memberFrom == null && memberTo == null && !reopen) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID)
        }
        val oldMemberFrom = membership.memberFrom
        val oldMemberTo = membership.memberTo
        val newMemberFrom = memberFrom ?: oldMemberFrom
        val newMemberTo = if (reopen) null else memberTo ?: oldMemberTo
        if (newMemberTo != null && !newMemberTo.isAfter(newMemberFrom)) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID, "memberTo must be after memberFrom.")
        }
        if (membershipRepository.overlapsOtherOfComponent(membershipId, membership.componentId, newMemberFrom, newMemberTo)
            || membershipRepository.overlapsOtherOfSlot(membershipId, membership.assemblySlotId, newMemberFrom, newMemberTo)
        ) {
            throw ServiceException(ErrorCodesDomain.MEMBERSHIP_OVERLAP)
        }

        val governed = mountingRepository.findAllByComponentIdAndAssemblyMountingIdIsNotNull(membership.componentId)
        val touched = governed.filter { it.mountedAt == oldMemberFrom || it.dismountedAt == oldMemberTo }
        try {
            touched.forEach { mounting ->
                if (mounting.mountedAt == oldMemberFrom) mounting.mountedAt = newMemberFrom
                if (mounting.dismountedAt == oldMemberTo) mounting.dismountedAt = newMemberTo
                // the overlap query auto-flushes this mounting's pending update first - a genuine
                // conflict surfaces here as a DIVE, not as a false query result, hence the shared catch
                if (mountingRepository.overlapsOtherOfComponent(mounting.id!!, mounting.componentId, mounting.mountedAt, mounting.dismountedAt)
                    || mountingRepository.overlapsOtherOfMountPoint(mounting.id!!, mounting.mountPointId, mounting.mountedAt, mounting.dismountedAt)
                ) {
                    throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP)
                }
            }
            mountingRepository.saveAllAndFlush(touched)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP, null, e)
        }

        membership.memberFrom = newMemberFrom
        membership.memberTo = newMemberTo
        try {
            membershipRepository.saveAndFlush(membership)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MEMBERSHIP_OVERLAP, null, e)
        }
        return toDomainMembership(membership)
    }

    /**
     * Erratum: the fact never happened - deliberate exception to "never
     * deleted" (mirrors MountingService.void). Allowed on closed memberships
     * only: voiding the active one would silently drop whatever it governs
     * with no replacement joining (use removeMember instead). Every governed
     * mounting overlapping the membership interval is resolved by provenance
     * (CE-0109): one this membership created is deleted, one adopted from a
     * pre-existing direct mounting is de-adopted instead of destroyed.
     */
    @Transactional
    fun voidMembership(membershipId: UUID) {
        val membership = membershipRepository.findById(membershipId)
            .orElseThrow { ServiceException(ErrorCodesDomain.MEMBERSHIP_NOT_FOUND) }
        if (membership.memberTo == null) {
            throw ServiceException(ErrorCodesDomain.MEMBERSHIP_VOID_BLOCKED,
                "Membership is active - use removeMember to end it.")
        }
        val governed = mountingRepository.findAllByComponentIdAndAssemblyMountingIdIsNotNull(membership.componentId)
        val overlapping = governed.filter {
            intervalsOverlap(it.mountedAt, it.dismountedAt, membership.memberFrom, membership.memberTo)
        }
        deAdoptOrDelete(overlapping)
        membershipRepository.delete(membership)
    }

    /**
     * Administrative correction of a mountedAt/dismountedAt data-entry error
     * on the assembly mounting itself (CE-0109). Cascades to every governed
     * mounting: mountedAt moves only for a row this assembly mounting (or a
     * membership within it) created - not for an adopted pre-existing direct
     * mounting, whose start predates governance; dismountedAt moves for both
     * kinds, since dismountAssembly imposed that boundary on adopted rows too.
     */
    @Transactional
    fun correctAssemblyMounting(
        assemblyId: UUID,
        mountingId: UUID,
        mountedAt: Instant?,
        dismountedAt: Instant?,
        reopen: Boolean = false,
    ): DomainAssemblyMounting {
        val assemblyMounting = requireAssemblyMounting(assemblyId, mountingId)
        if (mountedAt == null && dismountedAt == null && !reopen) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID)
        }
        val oldMountedAt = assemblyMounting.mountedAt
        val oldDismountedAt = assemblyMounting.dismountedAt
        val newMountedAt = mountedAt ?: oldMountedAt
        val newDismountedAt = if (reopen) null else dismountedAt ?: oldDismountedAt
        if (newDismountedAt != null && !newDismountedAt.isAfter(newMountedAt)) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID, "dismountedAt must be after mountedAt.")
        }
        if (assemblyMountingRepository.overlapsOtherOfAssembly(mountingId, assemblyId, newMountedAt, newDismountedAt)) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_MOUNTING_OVERLAP)
        }

        val governed = mountingRepository.findAllByAssemblyMountingId(mountingId)
        val touched = governed.filter { (it.mountedAt == oldMountedAt && !it.adopted) || it.dismountedAt == oldDismountedAt }
        try {
            touched.forEach { mounting ->
                if (mounting.mountedAt == oldMountedAt && !mounting.adopted) mounting.mountedAt = newMountedAt
                if (mounting.dismountedAt == oldDismountedAt) mounting.dismountedAt = newDismountedAt
                // the overlap query auto-flushes this mounting's pending update first - a genuine
                // conflict surfaces here as a DIVE, not as a false query result, hence the shared catch
                if (mountingRepository.overlapsOtherOfComponent(mounting.id!!, mounting.componentId, mounting.mountedAt, mounting.dismountedAt)
                    || mountingRepository.overlapsOtherOfMountPoint(mounting.id!!, mounting.mountPointId, mounting.mountedAt, mounting.dismountedAt)
                ) {
                    throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP)
                }
            }
            mountingRepository.saveAllAndFlush(touched)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP, null, e)
        }

        assemblyMounting.mountedAt = newMountedAt
        assemblyMounting.dismountedAt = newDismountedAt
        try {
            assemblyMountingRepository.saveAndFlush(assemblyMounting)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_MOUNTING_OVERLAP, null, e)
        }
        return mapper.map(assemblyMounting)
    }

    /**
     * Erratum: the assembly mounting never happened. Allowed on active and
     * closed assembly mountings alike (CE-0109) - unlike voidMembership,
     * there's no dismount-first requirement: on an active mounting, adopted
     * rows are still open, so de-adoption restores them exactly, whereas
     * forcing a dismount first would stamp a fake dismountedAt that
     * de-adoption couldn't undo. Every governed mounting is resolved by
     * provenance, then the assembly mounting row itself is deleted.
     */
    @Transactional
    fun voidAssemblyMounting(assemblyId: UUID, mountingId: UUID) {
        val assemblyMounting = requireAssemblyMounting(assemblyId, mountingId)
        val governed = mountingRepository.findAllByAssemblyMountingId(mountingId)
        deAdoptOrDelete(governed)
        assemblyMountingRepository.delete(assemblyMounting)
    }

    /** Provenance cascade shared by voidMembership/voidAssemblyMounting: created -> delete, adopted -> de-adopt. */
    private fun deAdoptOrDelete(mountings: List<MountingEntity>) {
        val (adopted, created) = mountings.partition { it.adopted }
        adopted.forEach {
            it.assemblyMountingId = null
            it.adopted = false
        }
        mountingRepository.saveAllAndFlush(adopted)
        mountingRepository.deleteAll(created)
    }

    private fun requireAssemblyMounting(assemblyId: UUID, mountingId: UUID): AssemblyMountingEntity {
        val assemblyMounting = assemblyMountingRepository.findById(mountingId)
            .orElseThrow { ServiceException(ErrorCodesDomain.ASSEMBLY_MOUNTING_NOT_FOUND) }
        if (assemblyMounting.assemblyId != assemblyId) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_MOUNTING_NOT_FOUND)
        }
        return assemblyMounting
    }

    private fun intervalsOverlap(aFrom: Instant, aTo: Instant?, bFrom: Instant, bTo: Instant?): Boolean {
        val aStartsBeforeBEnds = bTo == null || aFrom.isBefore(bTo)
        val bStartsBeforeAEnds = aTo == null || bFrom.isBefore(aTo)
        return aStartsBeforeBEnds && bStartsBeforeAEnds
    }

    private fun toDomainMembership(entity: AssemblyMembershipEntity): DomainAssemblyMembership {
        val assemblyId = slotRepository.findById(entity.assemblySlotId).orElseThrow().assemblyId
        return DomainAssemblyMembership(
            id = entity.id!!,
            componentId = entity.componentId,
            assemblySlotId = entity.assemblySlotId,
            assemblyId = assemblyId,
            memberFrom = entity.memberFrom,
            memberTo = entity.memberTo,
            createdAt = entity.createdAt
        )
    }

    /** ADR-0003 steps 1-4 + occupant/ownership checks for one filled slot. */
    private fun planSlot(
        slotId: UUID,
        componentId: UUID,
        componentTypeId: UUID,
        assemblyPositionId: UUID?,
        bikeId: UUID,
        at: Instant,
        userAnswer: UUID?,
    ): DomainPlannedSlot {
        val candidateEntities = mountPointRepository.findAllByBikeIdAndComponentTypeId(bikeId, componentTypeId)
        val candidates = candidateEntities.map { ResolverCandidate(it.id!!, it.name, it.positionId) }
        val storedMapping = slotMappingRepository.findByAssemblySlotIdAndBikeId(slotId, bikeId)?.mountPointId

        return when (val outcome = SlotResolver.resolveSlot(candidates, assemblyPositionId, storedMapping, userAnswer)) {
            is SlotResolutionOutcome.Unmountable -> DomainPlannedSlot(
                slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.IMPOSSIBLE,
                reasonCode = when (outcome.reasonCode) {
                    UnmountableReason.NO_CANDIDATE -> DomainImpossibleReason.NO_CANDIDATE
                    UnmountableReason.POSITION_FILTER_EMPTY -> DomainImpossibleReason.POSITION_FILTER_EMPTY
                },
                reason = "No mount point on this bike resolves this slot."
            )
            is SlotResolutionOutcome.Unresolved -> DomainPlannedSlot(
                slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.UNRESOLVED,
                candidates = outcome.candidates.map { DomainCandidate(it.mountPointId, it.mountPointName, it.positionId) }
            )
            is SlotResolutionOutcome.Resolved -> {
                val ownMounting = mountingRepository.findByComponentIdActiveAt(componentId, at)
                if (ownMounting != null && ownMounting.mountPointId != outcome.mountPointId) {
                    DomainPlannedSlot(
                        slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.IMPOSSIBLE,
                        reasonCode = DomainImpossibleReason.MEMBER_MOUNTED_ELSEWHERE,
                        reason = "Component is mounted at a different mount point."
                    )
                } else {
                    val occupant = mountingRepository.findByMountPointIdActiveAt(outcome.mountPointId, at)
                    if (occupant != null && occupant.componentId != componentId && occupant.assemblyMountingId != null) {
                        DomainPlannedSlot(
                            slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.RESOLVED,
                            mountPointId = outcome.mountPointId, resolvedBy = outcome.resolvedBy,
                            willDismountAssemblyMountingId = occupant.assemblyMountingId
                        )
                    } else {
                        val willDismount = if (occupant != null && occupant.componentId != componentId) occupant.componentId else null
                        DomainPlannedSlot(
                            slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.RESOLVED,
                            mountPointId = outcome.mountPointId, resolvedBy = outcome.resolvedBy,
                            willDismountComponentId = willDismount
                        )
                    }
                }
            }
        }
    }

    /** Resolves + applies the governed mounting side-effect of addMember while the assembly is mounted. */
    private fun mountMemberIntoGovernedSlot(
        assembly: ComponentAssemblyEntity,
        componentTypeId: UUID,
        slotId: UUID,
        componentId: UUID,
        activeAssemblyMounting: AssemblyMountingEntity,
        from: Instant,
        userAnswer: UUID?,
    ): DomainMountingChanges {
        val bikeId = activeAssemblyMounting.bikeId
        val planned = planSlot(slotId, componentId, componentTypeId, assembly.positionId, bikeId, from, userAnswer)
        if (planned.willDismountAssemblyMountingId != null) {
            // a single member joining a mounted assembly must not silently dismount a whole other assembly
            throw ServiceException(ErrorCodesDomain.MOUNTING_GOVERNED)
        }
        when (planned.state) {
            DomainPlannedSlotState.UNRESOLVED -> throw ServiceException(
                ErrorCodesDomain.UNRESOLVED_SLOTS, null, mapOf("candidates" to planned.candidates)
            )
            DomainPlannedSlotState.IMPOSSIBLE -> throw ServiceException(
                when (planned.reasonCode) {
                    DomainImpossibleReason.MEMBER_MOUNTED_ELSEWHERE -> ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE
                    else -> ErrorCodesDomain.SLOT_UNMOUNTABLE
                }
            )
            else -> Unit
        }
        val mountPointId = planned.mountPointId!!
        val ownMounting = mountingRepository.findByComponentIdActiveAt(componentId, from)
        val closed = mutableListOf<MountingEntity>()
        if (ownMounting != null && ownMounting.dismountedAt == null && ownMounting.mountPointId == mountPointId) {
            ownMounting.assemblyMountingId = activeAssemblyMounting.id
            ownMounting.adopted = true
            mountingRepository.saveAndFlush(ownMounting)
        } else {
            val occupant = mountingRepository.findByMountPointIdActiveAt(mountPointId, from)
            if (occupant != null) {
                if (!from.isAfter(occupant.mountedAt)) {
                    throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Occupant mounting started at ${occupant.mountedAt}.")
                }
                occupant.dismountedAt = from
                closed.add(occupant)
            }
            val created = try {
                mountingRepository.saveAllAndFlush(closed)
                mountingRepository.saveAndFlush(MountingEntity(id = null, componentId = componentId,
                    mountPointId = mountPointId, assemblyMountingId = activeAssemblyMounting.id, mountedAt = from))
            } catch (e: DataIntegrityViolationException) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP, null, e)
            }
            if (userAnswer != null && slotMappingRepository.findByAssemblySlotIdAndBikeId(slotId, bikeId) == null) {
                slotMappingRepository.saveAndFlush(
                    SlotMappingEntity(id = null, assemblySlotId = slotId, bikeId = bikeId, mountPointId = mountPointId)
                )
            }
            return DomainMountingChanges(
                created = listOf(toDomainMounting(created, bikeId, mountPointName(mountPointId, bikeId))),
                closed = closed.map { toDomainMounting(it, bikeId, mountPointName(it.mountPointId, bikeId)) }
            )
        }
        return DomainMountingChanges()
    }

    private fun mountPointName(mountPointId: UUID, bikeId: UUID): String =
        mountPointRepository.findByIdAndBikeId(mountPointId, bikeId)?.name ?: ""

    private fun toDomainMounting(entity: MountingEntity, bikeId: UUID, mountPointName: String) = DomainMounting(
        id = entity.id!!,
        componentId = entity.componentId,
        mountPointId = entity.mountPointId,
        bikeId = bikeId,
        mountPointName = mountPointName,
        assemblyMountingId = entity.assemblyMountingId,
        assemblyId = entity.assemblyMountingId?.let { assemblyMountingRepository.findById(it).orElseThrow().assemblyId },
        mountedAt = entity.mountedAt,
        dismountedAt = entity.dismountedAt,
        createdAt = entity.createdAt
    )

    private fun requireAssembly(assemblyId: UUID): ComponentAssemblyEntity =
        assemblyRepository.findById(assemblyId).orElseThrow { ServiceException(ErrorCodesDomain.ASSEMBLY_NOT_FOUND) }

    private fun requireBike(bikeId: UUID) {
        if (!bikeRepository.existsById(bikeId)) {
            throw ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND)
        }
    }
}
