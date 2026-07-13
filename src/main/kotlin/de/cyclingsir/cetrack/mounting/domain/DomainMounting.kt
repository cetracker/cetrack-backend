package de.cyclingsir.cetrack.mounting.domain

import java.time.Instant
import java.util.UUID

/**
 * Denormalized read view of the Mounting fact (carries the owning bike and
 * mount point name for resolvability, per the contract).
 */
data class DomainMounting(
    val id: UUID,
    val componentId: UUID,
    val mountPointId: UUID,
    val bikeId: UUID,
    val mountPointName: String,
    val assemblyMountingId: UUID? = null,
    val assemblyId: UUID? = null,
    val mountedAt: Instant,
    val dismountedAt: Instant? = null,
    val createdAt: Instant? = null
)

/**
 * Effects envelope of every action creating or closing Mountings (ADR-0001 §2
 * changes are domain-mandated notifications). The wire envelope also carries
 * membershipChanges - a typed domain field arrives with CE-0086, when a
 * mounted assembly can exist at all.
 */
data class DomainMountingChanges(
    val created: List<DomainMounting> = emptyList(),
    val closed: List<DomainMounting> = emptyList(),
    val membershipChanges: List<DomainMembershipChange> = emptyList(),
)
