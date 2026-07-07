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
                planSlot(slot.slotId, componentId, slot.componentTypeId, assembly.positionId, bikeId, userAnswer = null)
            }
        }
        val mountable = planned.isNotEmpty() && planned.all { it.state == DomainPlannedSlotState.RESOLVED }
        return DomainMountPlan(assemblyId = assemblyId, bikeId = bikeId, at = at, mountable = mountable, slots = planned)
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
                slot.slotId, slot.memberComponentId!!, slot.componentTypeId, assembly.positionId, bikeId,
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
        val governedOccupant = unmountable.filter { it.reasonCode == DomainImpossibleReason.OCCUPIED_BY_GOVERNED_MOUNTING }
        if (governedOccupant.isNotEmpty()) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_GOVERNED)
        }
        val resolvedMountPointBySlot = planned.associate { it.slotId to it.mountPointId!! }
        val colliding = SlotResolver.collidingSlotIds(resolvedMountPointBySlot)
        if (colliding.isNotEmpty()) {
            throw ServiceException(ErrorCodesDomain.SLOT_TARGET_COLLISION, null, mapOf("slotIds" to colliding))
        }

        // backdate pre-check before any mutation - direct occupants this mount would evict
        planned.forEach { slot ->
            val occupant = mountingRepository.findByMountPointIdAndDismountedAtIsNull(slot.mountPointId!!)
            if (occupant != null && occupant.componentId != slot.componentId && !at.isAfter(occupant.mountedAt)) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED,
                    "Occupant mounting to close started at ${occupant.mountedAt}.")
            }
        }

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
            val ownMounting = mountingRepository.findByComponentIdAndDismountedAtIsNull(componentId)
            if (ownMounting != null && ownMounting.mountPointId == mountPointId) {
                ownMounting.assemblyMountingId = assemblyMountingId
                toClose.add(ownMounting) // reused as the "touch" list, saved below
            } else {
                val occupant = mountingRepository.findByMountPointIdAndDismountedAtIsNull(mountPointId)
                if (occupant != null) {
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
            closed = closed.map { toDomainMounting(it, bikeId, mountPointNames[it.mountPointId]!!) }
        )
        return DomainAssemblyMountResult(
            assemblyMounting = mapper.map(assemblyMounting),
            changes = changes,
            rememberedSlotMappings = remembered.map {
                DomainSlotMapping(it.id, it.assemblySlotId, it.bikeId, it.mountPointId, it.createdAt)
            }
        )
    }

    @Transactional
    fun dismountAssembly(assemblyId: UUID, at: Instant): DomainAssemblyMountResult {
        requireAssembly(assemblyId)
        val active = assemblyMountingRepository.findByAssemblyIdAndDismountedAtIsNull(assemblyId)
            ?: throw ServiceException(ErrorCodesDomain.ASSEMBLY_NOT_MOUNTED)
        if (!at.isAfter(active.mountedAt)) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Assembly mounting started at ${active.mountedAt}.")
        }
        val governed = mountingRepository.findAllByAssemblyMountingIdAndDismountedAtIsNull(active.id!!)
        governed.forEach { mounting ->
            if (!at.isAfter(mounting.mountedAt)) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED, "Mounting to close started at ${mounting.mountedAt}.")
            }
        }
        governed.forEach { it.dismountedAt = at }
        active.dismountedAt = at
        mountingRepository.saveAllAndFlush(governed)
        assemblyMountingRepository.saveAndFlush(active)

        val changes = DomainMountingChanges(
            closed = governed.map { toDomainMounting(it, active.bikeId, mountPointName(it.mountPointId, active.bikeId)) }
        )
        return DomainAssemblyMountResult(assemblyMounting = mapper.map(active), changes = changes)
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

    /** ADR-0003 steps 1-4 + occupant/ownership checks for one filled slot. */
    private fun planSlot(
        slotId: UUID,
        componentId: UUID,
        componentTypeId: UUID,
        assemblyPositionId: UUID?,
        bikeId: UUID,
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
                val ownMounting = mountingRepository.findByComponentIdAndDismountedAtIsNull(componentId)
                if (ownMounting != null && ownMounting.mountPointId != outcome.mountPointId) {
                    DomainPlannedSlot(
                        slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.IMPOSSIBLE,
                        reasonCode = DomainImpossibleReason.MEMBER_MOUNTED_ELSEWHERE,
                        reason = "Component is mounted at a different mount point."
                    )
                } else {
                    val occupant = mountingRepository.findByMountPointIdAndDismountedAtIsNull(outcome.mountPointId)
                    if (occupant != null && occupant.componentId != componentId && occupant.assemblyMountingId != null) {
                        DomainPlannedSlot(
                            slotId = slotId, componentId = componentId, state = DomainPlannedSlotState.IMPOSSIBLE,
                            reasonCode = DomainImpossibleReason.OCCUPIED_BY_GOVERNED_MOUNTING,
                            reason = "Mount point is occupied by another assembly's governed mounting."
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
        val planned = planSlot(slotId, componentId, componentTypeId, assembly.positionId, bikeId, userAnswer)
        when (planned.state) {
            DomainPlannedSlotState.UNRESOLVED -> throw ServiceException(
                ErrorCodesDomain.UNRESOLVED_SLOTS, null, mapOf("candidates" to planned.candidates)
            )
            DomainPlannedSlotState.IMPOSSIBLE -> throw ServiceException(
                when (planned.reasonCode) {
                    DomainImpossibleReason.MEMBER_MOUNTED_ELSEWHERE -> ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE
                    DomainImpossibleReason.OCCUPIED_BY_GOVERNED_MOUNTING -> ErrorCodesDomain.MOUNTING_GOVERNED
                    else -> ErrorCodesDomain.SLOT_UNMOUNTABLE
                }
            )
            else -> Unit
        }
        val mountPointId = planned.mountPointId!!
        val ownMounting = mountingRepository.findByComponentIdAndDismountedAtIsNull(componentId)
        val closed = mutableListOf<MountingEntity>()
        if (ownMounting != null && ownMounting.mountPointId == mountPointId) {
            ownMounting.assemblyMountingId = activeAssemblyMounting.id
            mountingRepository.saveAndFlush(ownMounting)
        } else {
            val occupant = mountingRepository.findByMountPointIdAndDismountedAtIsNull(mountPointId)
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
