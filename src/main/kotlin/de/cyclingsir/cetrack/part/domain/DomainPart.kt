package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.infrastructure.api.model.PartType
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
data class DomainPart(
    val id: UUID?,
    val name: String,
    val boughtAt: Instant?,
    val partTypes: MutableList<PartType> = mutableListOf(),
    val createdAt: Instant?
)
