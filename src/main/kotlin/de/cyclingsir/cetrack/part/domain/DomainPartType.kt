package de.cyclingsir.cetrack.part.domain

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
data class DomainPartType(
    val id: UUID,
    val name: @NotNull String,
    val createdAt: Instant? = null)
