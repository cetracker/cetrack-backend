package de.cyclingsir.cetrack.part.domain

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
@SuppressWarnings("EI_EXPOSE_REP")
data class DomainPart(
    val id: UUID?,
    val name: @NotNull String,
    val boughtAt: Instant?,
    val retiredAt: Instant?,
    val partTypeRelations: List<DomainPartPartTypeRelation>?,
    val createdAt: Instant?
)
