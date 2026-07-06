package de.cyclingsir.cetrack.bike.domain

import java.time.Instant
import java.util.UUID

data class DomainMountPoint(
    val id: UUID? = null,
    val bikeId: UUID,
    val componentTypeId: UUID,
    val positionId: UUID? = null,
    val name: String,
    val mandatory: Boolean = false,
    val createdAt: Instant? = null
)

data class DomainSlotMapping(
    val id: UUID? = null,
    val assemblySlotId: UUID,
    val bikeId: UUID,
    val mountPointId: UUID,
    val createdAt: Instant? = null
)
