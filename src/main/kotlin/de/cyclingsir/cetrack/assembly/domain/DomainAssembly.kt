package de.cyclingsir.cetrack.assembly.domain

import java.time.Instant
import java.util.UUID

data class DomainAssembly(
    val id: UUID? = null,
    val name: String,
    val positionId: UUID? = null,
    val complete: Boolean = false,
    val mounted: Boolean = false,
    val slots: List<DomainAssemblySlot> = emptyList(),
    val createdAt: Instant? = null,
)

/** Read view of one slot with the member active at the requested time, if any. */
data class DomainAssemblySlot(
    val id: UUID? = null,
    val assemblyId: UUID,
    val name: String,
    val componentTypeId: UUID,
    val validFrom: Instant,
    val validTo: Instant? = null,
    val memberComponentId: UUID? = null,
    val memberFrom: Instant? = null,
    val createdAt: Instant? = null,
)

/** Temporal fact - the assembly as a unit on a bike over [mountedAt, dismountedAt). */
data class DomainAssemblyMounting(
    val id: UUID? = null,
    val assemblyId: UUID,
    val bikeId: UUID,
    val mountedAt: Instant,
    val dismountedAt: Instant? = null,
    val createdAt: Instant? = null,
)
