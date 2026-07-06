package de.cyclingsir.cetrack.catalog.domain

import java.time.Instant
import java.util.UUID

data class DomainComponentType(
    val id: UUID? = null,
    val name: String,
    val description: String? = null,
    val createdAt: Instant? = null
)

data class DomainPosition(
    val id: UUID? = null,
    val name: String,
    val createdAt: Instant? = null
)
