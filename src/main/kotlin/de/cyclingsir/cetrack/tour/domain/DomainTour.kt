package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.domain.DomainBike
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
data class DomainTour(
    val id: UUID?,
    val title: String,
    val length: Int,
    val duration: Duration,
    val bike: DomainBike?,
    val startedAt: Instant,
    val createdAt: Instant?
)
