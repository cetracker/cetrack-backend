package de.cyclingsir.cetrack.mounting.domain

import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.bike.storage.MountPointRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.storage.ComponentRepository
import de.cyclingsir.cetrack.mounting.storage.MountingEntity
import de.cyclingsir.cetrack.mounting.storage.MountingRepository
import de.cyclingsir.cetrack.mounting.storage.MountingWithPlace
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * The domain operations of domain-model.md §4 with ADR-0001 semantics.
 * Each command is one transaction; the DB exclusion constraints remain the
 * concurrent-safety net (mapped to 409 MOUNTING_OVERLAP).
 */
@Service
class MountingService(
    private val mountingRepository: MountingRepository,
    private val mountPointRepository: MountPointRepository,
    private val bikeRepository: BikeRepository,
    private val componentRepository: ComponentRepository,
    private val membershipPropagation: MembershipPropagation,
) {

    /**
     * mount(component, mountPoint, at) - §4: validity checks, guided choice
     * (ADR-0001 §3), then auto-dismount of the mount point's previous occupant
     * AND of the component's own current mounting.
     */
    @Transactional
    fun mount(bikeId: UUID, mountPointId: UUID, componentId: UUID, at: Instant): DomainMountingChanges {
        val mountPoint = mountPointRepository.findByIdAndBikeId(mountPointId, bikeId)
            ?: throw ServiceException(ErrorCodesDomain.MOUNT_POINT_NOT_FOUND)
        val bike = bikeRepository.findById(bikeId)
            .orElseThrow { ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND) }
        if (bike.retiredAt != null) {
            throw ServiceException(ErrorCodesDomain.BIKE_RETIRED)
        }
        val component = componentRepository.findById(componentId)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND) }
        if (component.retiredAt != null) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_RETIRED, "Retired components can't be mounted.")
        }
        if (component.componentTypeId != mountPoint.componentTypeId) {
            throw ServiceException(ErrorCodesDomain.TYPE_MISMATCH)
        }
        mountingRepository.findUnmountedAssemblyOfActiveMembership(componentId)?.let { assemblyId ->
            throw ServiceException(
                ErrorCodesDomain.ASSEMBLY_MEMBER_GUIDED_CHOICE,
                "Mount the complete assembly instead, or remove the membership first (ADR-0001 §3).",
                mapOf(
                    "assemblyId" to assemblyId,
                    "options" to listOf("MOUNT_ASSEMBLY_INSTEAD", "REMOVE_MEMBERSHIP_THEN_MOUNT")
                )
            )
        }

        val occupant = mountingRepository.findByMountPointIdAndDismountedAtIsNull(mountPointId)
        val own = mountingRepository.findByComponentIdAndDismountedAtIsNull(componentId)
        // ADR-0001 §2 (ruling 2b, first bullet): the component itself governed elsewhere is
        // rejected before the occupant is touched - auto-dismounting it here would silently
        // rip it out of its mounted assembly with no replacement joining. The degenerate
        // self-case (own IS the occupant, same mount point) is not "elsewhere" - no reject.
        if (own != null && own.assemblyMountingId != null && own.mountPointId != mountPointId) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_GOVERNED, "Dismount the assembly instead.")
        }

        val toClose = listOfNotNull(occupant, own).distinctBy { it.id }
        toClose.forEach { mounting ->
            if (!at.isAfter(mounting.mountedAt)) {
                throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED,
                    "Mounting to close started at ${mounting.mountedAt}.")
            }
        }
        // ADR-0001 §2 (ruling 2b, second bullet): a governed occupant of a MOUNTED assembly
        // propagates membership to the replacement instead of rejecting the mount.
        val propagateOccupant = occupant != null && occupant.assemblyMountingId != null && occupant.componentId != componentId
        toClose.forEach { it.dismountedAt = at }

        val newMounting = MountingEntity(id = null, componentId = componentId, mountPointId = mountPointId, mountedAt = at)
        val membershipChanges = if (propagateOccupant) {
            membershipPropagation.propagate(occupant, newMounting, at)
        } else {
            emptyList()
        }

        val created = try {
            // flush the closings first: the exclusion constraint is checked per
            // statement and Hibernate would otherwise order the INSERT first
            mountingRepository.saveAllAndFlush(toClose)
            mountingRepository.saveAndFlush(newMounting)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP, null, e)
        }
        logger.info { "Mounted $componentId at $mountPointId ($at); closed ${toClose.map { it.id }}" }

        return DomainMountingChanges(
            created = listOf(toDomain(created)),
            closed = toClose.map(::toDomain),
            membershipChanges = membershipChanges
        )
    }

    /** dismount(component, at) - only direct mountings; assembly-governed ones dismount with the assembly. */
    @Transactional
    fun dismount(componentId: UUID, at: Instant): DomainMountingChanges {
        if (!componentRepository.existsById(componentId)) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND)
        }
        val active = mountingRepository.findByComponentIdAndDismountedAtIsNull(componentId)
            ?: throw ServiceException(ErrorCodesDomain.NOT_MOUNTED)
        if (active.assemblyMountingId != null) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_GOVERNED, "Dismount the assembly instead.")
        }
        if (!at.isAfter(active.mountedAt)) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_BACKDATED,
                "Mounting started at ${active.mountedAt}.")
        }
        active.dismountedAt = at
        mountingRepository.saveAndFlush(active)
        return DomainMountingChanges(closed = listOf(toDomain(active)))
    }

    @Transactional(readOnly = true)
    fun getMountings(componentId: UUID?, mountPointId: UUID?, bikeId: UUID?, activeAt: Instant?): List<DomainMounting> =
        mountingRepository.findWithPlace(componentId, mountPointId, bikeId, activeAt).map(::toDomain)

    @Transactional(readOnly = true)
    fun getMounting(mountingId: UUID): DomainMounting =
        mountingRepository.findWithPlaceById(mountingId)
            ?.let(::toDomain)
            ?: throw ServiceException(ErrorCodesDomain.MOUNTING_NOT_FOUND)

    /**
     * Administrative correction of a data-entry error - re-validated like a
     * mount (no overlaps per component and per mount point). Tri-state
     * dismountedAt: a value sets it, null keeps the current value,
     * [reopenDismount] clears it - the mounting becomes active again with an
     * open-ended interval (spec: explicit-null dismountedAt).
     */
    @Transactional
    fun correct(
        mountingId: UUID,
        mountedAt: Instant?,
        dismountedAt: Instant?,
        reopenDismount: Boolean = false,
    ): DomainMounting {
        val mounting = mountingRepository.findById(mountingId)
            .orElseThrow { ServiceException(ErrorCodesDomain.MOUNTING_NOT_FOUND) }
        if (mounting.assemblyMountingId != null) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_GOVERNED,
                "Correct it in the assembly slot member history instead.")
        }
        if (mountedAt == null && dismountedAt == null && !reopenDismount) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID)
        }
        val newMountedAt = mountedAt ?: mounting.mountedAt
        val newDismountedAt = if (reopenDismount) null else dismountedAt ?: mounting.dismountedAt
        if (newDismountedAt != null && !newDismountedAt.isAfter(newMountedAt)) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID,
                "dismountedAt must be after mountedAt.")
        }
        if (mountingRepository.overlapsOtherOfComponent(mountingId, mounting.componentId, newMountedAt, newDismountedAt)
            || mountingRepository.overlapsOtherOfMountPoint(mountingId, mounting.mountPointId, newMountedAt, newDismountedAt)
        ) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP)
        }
        mounting.mountedAt = newMountedAt
        mounting.dismountedAt = newDismountedAt
        try {
            mountingRepository.saveAndFlush(mounting)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_OVERLAP, null, e)
        }
        return getMounting(mountingId)
    }

    /** Erratum: the fact never happened - deliberate exception to §3 "never deleted". */
    @Transactional
    fun void(mountingId: UUID) {
        val mounting = mountingRepository.findById(mountingId)
            .orElseThrow { ServiceException(ErrorCodesDomain.MOUNTING_NOT_FOUND) }
        if (mounting.assemblyMountingId != null) {
            throw ServiceException(ErrorCodesDomain.MOUNTING_GOVERNED,
                "Void it in the assembly slot member history instead.")
        }
        mountingRepository.delete(mounting)
    }

    private fun toDomain(projection: MountingWithPlace) = DomainMounting(
        id = projection.id,
        componentId = projection.componentId,
        mountPointId = projection.mountPointId,
        bikeId = projection.bikeId,
        mountPointName = projection.mountPointName,
        assemblyMountingId = projection.assemblyMountingId,
        assemblyId = projection.assemblyId,
        mountedAt = projection.mountedAt,
        dismountedAt = projection.dismountedAt,
        createdAt = projection.createdAt
    )

    /** Callers flush [entity] before mapping, so the joined projection always resolves it. */
    private fun toDomain(entity: MountingEntity): DomainMounting =
        mountingRepository.findWithPlaceById(entity.id!!)
            ?.let(::toDomain)
            ?: throw ServiceException(ErrorCodesDomain.MOUNTING_NOT_FOUND)
}
