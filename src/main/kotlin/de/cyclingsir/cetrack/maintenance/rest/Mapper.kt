package de.cyclingsir.cetrack.maintenance.rest

import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceDue
import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceEvent
import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceTask
import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceTaskInput
import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceDue
import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceEvent
import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceTask
import java.time.ZoneOffset

class MaintenanceDomain2ApiMapper {

    fun map(domain: DomainMaintenanceTask): MaintenanceTask = MaintenanceTask(
        id = domain.id,
        bikeId = domain.bikeId,
        name = domain.name,
        distanceInterval = domain.distanceInterval,
        timeInterval = domain.timeInterval,
        due = domain.due?.let(::map),
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(rest: MaintenanceTaskInput): DomainMaintenanceTask = DomainMaintenanceTask(
        bikeId = rest.bikeId,
        name = rest.name,
        distanceInterval = rest.distanceInterval,
        timeInterval = rest.timeInterval
    )

    fun map(domain: DomainMaintenanceDue): MaintenanceDue = MaintenanceDue(
        due = domain.due,
        lastPerformedAt = domain.lastPerformedAt?.atOffset(ZoneOffset.UTC),
        distanceSinceLast = domain.distanceSinceLast,
        distanceRemaining = domain.distanceRemaining,
        timeSinceLast = domain.timeSinceLast,
        timeRemaining = domain.timeRemaining
    )

    fun map(domain: DomainMaintenanceEvent): MaintenanceEvent = MaintenanceEvent(
        id = domain.id!!,
        maintenanceTaskId = domain.maintenanceTaskId,
        performedAt = domain.performedAt.atOffset(ZoneOffset.UTC),
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC),
        distanceSincePrevious = domain.distanceSincePrevious
    )
}
