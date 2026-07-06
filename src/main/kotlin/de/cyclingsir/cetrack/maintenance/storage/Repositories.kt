package de.cyclingsir.cetrack.maintenance.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MaintenanceTaskRepository : JpaRepository<MaintenanceTaskEntity, UUID> {

    fun findAllByBikeId(bikeId: UUID): List<MaintenanceTaskEntity>
}

@Repository
interface MaintenanceEventRepository : JpaRepository<MaintenanceEventEntity, UUID> {

    fun findAllByMaintenanceTaskIdOrderByPerformedAtDesc(maintenanceTaskId: UUID): List<MaintenanceEventEntity>

    fun findFirstByMaintenanceTaskIdOrderByPerformedAtDesc(maintenanceTaskId: UUID): MaintenanceEventEntity?

    fun findByIdAndMaintenanceTaskId(id: UUID, maintenanceTaskId: UUID): MaintenanceEventEntity?

    fun deleteByMaintenanceTaskId(maintenanceTaskId: UUID)
}
