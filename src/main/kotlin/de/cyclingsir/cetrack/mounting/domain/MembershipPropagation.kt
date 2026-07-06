package de.cyclingsir.cetrack.mounting.domain

import de.cyclingsir.cetrack.mounting.storage.MountingEntity
import java.time.Instant
import java.util.UUID

enum class DomainMembershipAction { ADDED, REMOVED }

data class DomainMembershipChange(
    val componentId: UUID,
    val assemblySlotId: UUID,
    val action: DomainMembershipAction,
    val at: Instant,
)

/**
 * ADR-0001 §2: when a direct mount replaces a governed occupant, assembly
 * membership auto-propagates to the replacement instead of the mount being
 * rejected. A port owned by mounting/domain, implemented in assembly/
 * (CE-0086), so this module never depends on the assembly module.
 */
fun interface MembershipPropagation {

    /**
     * [occupant] is the governed Mounting about to close (its
     * assemblyMountingId names the AssemblyMounting it belongs to).
     * [replacement] is the not-yet-persisted Mounting for the incoming
     * component - mount() creates it with null provenance, so this function
     * stamps its assemblyMountingId with the occupant's before it's saved.
     * Returns the membership side effects for the response envelope.
     */
    fun propagate(occupant: MountingEntity, replacement: MountingEntity, at: Instant): List<DomainMembershipChange>
}
