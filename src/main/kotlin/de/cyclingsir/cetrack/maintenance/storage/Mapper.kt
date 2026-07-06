package de.cyclingsir.cetrack.maintenance.storage

import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceEvent
import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceTask

class MaintenanceDomain2StorageMapper {

    fun map(domain: DomainMaintenanceTask): MaintenanceTaskEntity = MaintenanceTaskEntity(
        id = domain.id,
        bikeId = domain.bikeId,
        name = domain.name,
        distanceInterval = domain.distanceInterval,
        timeInterval = domain.timeInterval,
        createdAt = domain.createdAt
    )

    fun map(jpa: MaintenanceTaskEntity): DomainMaintenanceTask = DomainMaintenanceTask(
        id = jpa.id,
        bikeId = jpa.bikeId,
        name = jpa.name,
        distanceInterval = jpa.distanceInterval,
        timeInterval = jpa.timeInterval,
        createdAt = jpa.createdAt
    )

    fun map(domain: DomainMaintenanceEvent): MaintenanceEventEntity = MaintenanceEventEntity(
        id = domain.id,
        maintenanceTaskId = domain.maintenanceTaskId,
        performedAt = domain.performedAt,
        createdAt = domain.createdAt
    )

    fun map(jpa: MaintenanceEventEntity): DomainMaintenanceEvent = DomainMaintenanceEvent(
        id = jpa.id,
        maintenanceTaskId = jpa.maintenanceTaskId,
        performedAt = jpa.performedAt,
        createdAt = jpa.createdAt
    )
}
