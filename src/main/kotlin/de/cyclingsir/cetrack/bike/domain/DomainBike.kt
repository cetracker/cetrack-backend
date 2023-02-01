package de.cyclingsir.cetrack.bike.domain

/**
 * Initially created on 2/1/23.
 */
data class DomainBike(
    val model: String,

    val manufacturer: String?,

    val id: java.util.UUID?,

    val boughtAt: java.time.Instant?,

    val createdAt: java.time.Instant?
)
