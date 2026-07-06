package de.cyclingsir.cetrack.bike.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
data class DomainBike(
    val id: UUID? = null,

    val name: String? = null,

    val model: String? = null,

    val manufacturer: String? = null,

    val purchaseDate: LocalDate? = null,

    val price: String? = null,

    val priceCurrency: String? = null,

    val retiredAt: Instant? = null,

    val createdAt: Instant? = null
)
