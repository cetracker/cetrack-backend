package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.DomainBike
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
data class DomainPartType(
    val id: UUID?,
    val name: @NotNull String,
    val bike: DomainBike?,
    val createdAt: Instant?)
