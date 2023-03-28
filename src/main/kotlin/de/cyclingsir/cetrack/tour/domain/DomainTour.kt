package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.domain.DomainBike
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
data class DomainTour(
    val id: UUID?,
    val mtTourId: String? = null,
    val title: String,
    val distance: Int,
    val durationMoving: Long,
    val altUp: Int,
    val altDown: Int,
    val bike: DomainBike?,
    val startedAt: Instant,
    val createdAt: Instant?
)
