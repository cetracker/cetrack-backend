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
}
