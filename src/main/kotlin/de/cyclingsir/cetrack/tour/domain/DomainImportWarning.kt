package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import java.time.Instant
import java.util.UUID

data class DomainImportWarning(
    val type: String,
    val mtTourId: String?,
    val message: String,
    val incomingCandidate: DomainMTTour? = null,
    val matchedTours: List<DomainTourSummary> = emptyList(),
    val replaceDisabled: Boolean = false
)

data class DomainTourSummary(
    val tourId: UUID,
    val title: String,
    val startedAt: Instant,
    val distance: Int,
    val durationMoving: Long,
    val bikeId: UUID?
)
