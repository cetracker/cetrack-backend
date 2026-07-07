package de.cyclingsir.cetrack.maintenance.domain

import java.time.Instant
import java.util.UUID

data class DomainMaintenanceTask(
    val id: UUID? = null,
    val bikeId: UUID,
    val name: String,
    val distanceInterval: Long? = null,
    val timeInterval: Long? = null,
    val due: DomainMaintenanceDue? = null,
    val createdAt: Instant? = null
)

data class DomainMaintenanceEvent(
    val id: UUID? = null,
    val maintenanceTaskId: UUID,
    val performedAt: Instant,
    val createdAt: Instant? = null,
    val distanceSincePrevious: Long? = null
)

/** Derived (domain-model.md §5.4): never stored, computed from tour history + events vs. the task's intervals. */
data class DomainMaintenanceDue(
    val due: Boolean,
    val lastPerformedAt: Instant? = null,
    val distanceSinceLast: Long,
    val distanceRemaining: Long? = null,
    val timeSinceLast: Long? = null,
    val timeRemaining: Long? = null
)
