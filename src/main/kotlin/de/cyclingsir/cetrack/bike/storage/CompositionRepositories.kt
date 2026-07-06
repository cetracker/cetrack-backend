package de.cyclingsir.cetrack.bike.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MountPointRepository : JpaRepository<MountPointEntity, UUID> {

    fun findAllByBikeId(bikeId: UUID): List<MountPointEntity>

    fun findByIdAndBikeId(id: UUID, bikeId: UUID): MountPointEntity?

    /** ADR-0003 step 1: the bike's mount points accepting a slot's ComponentType. */
    fun findAllByBikeIdAndComponentTypeId(bikeId: UUID, componentTypeId: UUID): List<MountPointEntity>

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM mounting m WHERE m.mount_point_id = :id AND m.dismounted_at IS NULL)"
    )
    fun hasActiveMounting(@Param("id") id: UUID): Boolean
}

@Repository
interface SlotMappingRepository : JpaRepository<SlotMappingEntity, UUID> {

    fun findAllByBikeId(bikeId: UUID): List<SlotMappingEntity>

    fun findByIdAndBikeId(id: UUID, bikeId: UUID): SlotMappingEntity?

    /** ADR-0003 step 4: the remembered resolution for this slot on this bike, if any. */
    fun findByAssemblySlotIdAndBikeId(assemblySlotId: UUID, bikeId: UUID): SlotMappingEntity?
}
