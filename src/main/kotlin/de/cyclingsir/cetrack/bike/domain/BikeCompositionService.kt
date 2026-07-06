package de.cyclingsir.cetrack.bike.domain

import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.bike.storage.MountPointEntity
import de.cyclingsir.cetrack.bike.storage.MountPointRepository
import de.cyclingsir.cetrack.bike.storage.SlotMappingRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * BikeComposition: the bike's set of MountPoints (+ the remembered ADR-0003
 * slot resolutions). MountPoints can be added/changed over the bike's life
 * (domain-model.md §3) - but the accepted type is locked while a mounting
 * is active, otherwise the type-match invariant would silently break.
 */
@Service
class BikeCompositionService(
    private val bikeRepository: BikeRepository,
    private val mountPointRepository: MountPointRepository,
    private val slotMappingRepository: SlotMappingRepository,
) {

    @Transactional(readOnly = true)
    fun getMountPoints(bikeId: UUID): List<DomainMountPoint> {
        requireBike(bikeId)
        return mountPointRepository.findAllByBikeId(bikeId).map(::toDomain)
    }

    @Transactional
    fun addMountPoint(mountPoint: DomainMountPoint): DomainMountPoint {
        requireBike(mountPoint.bikeId)
        val entity = MountPointEntity(
            id = null,
            bikeId = mountPoint.bikeId,
            componentTypeId = mountPoint.componentTypeId,
            positionId = mountPoint.positionId,
            name = mountPoint.name,
            mandatory = mountPoint.mandatory
        )
        return try {
            toDomain(mountPointRepository.saveAndFlush(entity))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNT_POINT_DATA_INVALID,
                "Mount point references an unknown component type or position.", e)
        }
    }

    @Transactional
    fun modifyMountPoint(bikeId: UUID, mountPointId: UUID, mountPoint: DomainMountPoint): DomainMountPoint {
        val existing = mountPointRepository.findByIdAndBikeId(mountPointId, bikeId)
            ?: throw ServiceException(ErrorCodesDomain.MOUNT_POINT_NOT_FOUND)
        if (existing.componentTypeId != mountPoint.componentTypeId
            && mountPointRepository.hasActiveMounting(mountPointId)
        ) {
            throw ServiceException(ErrorCodesDomain.MOUNT_POINT_IN_USE,
                "Accepted type can't change while a mounting is active; dismount first.")
        }
        existing.componentTypeId = mountPoint.componentTypeId
        existing.positionId = mountPoint.positionId
        existing.name = mountPoint.name
        existing.mandatory = mountPoint.mandatory
        return try {
            toDomain(mountPointRepository.saveAndFlush(existing))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MOUNT_POINT_DATA_INVALID,
                "Mount point references an unknown component type or position.", e)
        }
    }

    @Transactional
    fun deleteMountPoint(bikeId: UUID, mountPointId: UUID) {
        val existing = mountPointRepository.findByIdAndBikeId(mountPointId, bikeId)
            ?: throw ServiceException(ErrorCodesDomain.MOUNT_POINT_NOT_FOUND)
        try {
            mountPointRepository.delete(existing)
            mountPointRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            // mounting or slot_mapping rows reference the mount point (FK RESTRICT)
            throw ServiceException(ErrorCodesDomain.MOUNT_POINT_IN_USE,
                "Mount point has mounting history or slot mappings.")
        }
    }

    @Transactional(readOnly = true)
    fun getSlotMappings(bikeId: UUID): List<DomainSlotMapping> {
        requireBike(bikeId)
        return slotMappingRepository.findAllByBikeId(bikeId).map {
            DomainSlotMapping(it.id, it.assemblySlotId, it.bikeId, it.mountPointId, it.createdAt)
        }
    }

    @Transactional
    fun deleteSlotMapping(bikeId: UUID, slotMappingId: UUID) {
        val existing = slotMappingRepository.findByIdAndBikeId(slotMappingId, bikeId)
            ?: throw ServiceException(ErrorCodesDomain.SLOT_MAPPING_NOT_FOUND)
        slotMappingRepository.delete(existing)
    }

    private fun requireBike(bikeId: UUID) {
        if (!bikeRepository.existsById(bikeId)) {
            throw ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND)
        }
    }

    private fun toDomain(entity: MountPointEntity) = DomainMountPoint(
        id = entity.id,
        bikeId = entity.bikeId,
        componentTypeId = entity.componentTypeId,
        positionId = entity.positionId,
        name = entity.name,
        mandatory = entity.mandatory,
        createdAt = entity.createdAt
    )
}
