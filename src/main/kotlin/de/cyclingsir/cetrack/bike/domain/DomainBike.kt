package de.cyclingsir.cetrack.bike.domain

import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
data class DomainBike(
    val model: String,

    val manufacturer: String?,

    val id: UUID?,

    val boughtAt: Instant?,

    val retiredAt: Instant?,

    val createdAt: Instant?
)
