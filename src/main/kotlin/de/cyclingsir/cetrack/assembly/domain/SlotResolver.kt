package de.cyclingsir.cetrack.assembly.domain

import java.util.UUID

/** One bike mount point eligible to receive a slot's ComponentType (ADR-0003 step 1). */
data class ResolverCandidate(val mountPointId: UUID, val mountPointName: String, val positionId: UUID? = null)

enum class ResolvedBy { UNIQUE_CANDIDATE, POSITION_FILTER, SLOT_MAPPING }
enum class UnmountableReason { NO_CANDIDATE, POSITION_FILTER_EMPTY }

sealed class SlotResolutionOutcome {
    data class Resolved(val mountPointId: UUID, val resolvedBy: ResolvedBy) : SlotResolutionOutcome()
    data class Unresolved(val candidates: List<ResolverCandidate>) : SlotResolutionOutcome()
    data class Unmountable(val reasonCode: UnmountableReason) : SlotResolutionOutcome()
}

/**
 * ADR-0003 steps 1-4 for one filled slot (candidate gathering is step 1,
 * done by the caller). Pure - no Spring, no persistence - so every branch is
 * unit-testable without a database.
 */
object SlotResolver {

    fun resolveSlot(
        candidates: List<ResolverCandidate>,
        assemblyPositionId: UUID?,
        storedMappingMountPointId: UUID?,
        userAnswerMountPointId: UUID?,
    ): SlotResolutionOutcome {
        if (candidates.isEmpty()) {
            return SlotResolutionOutcome.Unmountable(UnmountableReason.NO_CANDIDATE)
        }
        // a single candidate is the only physically possible place regardless of Position
        // tagging - no ambiguity to resolve, so the Position filter never runs.
        if (candidates.size == 1) {
            return SlotResolutionOutcome.Resolved(candidates.single().mountPointId, ResolvedBy.UNIQUE_CANDIDATE)
        }
        val filtered = if (assemblyPositionId != null) {
            candidates.filter { it.positionId == assemblyPositionId }
        } else {
            candidates
        }
        if (assemblyPositionId != null && filtered.isEmpty()) {
            return SlotResolutionOutcome.Unmountable(UnmountableReason.POSITION_FILTER_EMPTY)
        }
        if (filtered.size == 1) {
            return SlotResolutionOutcome.Resolved(filtered.single().mountPointId, ResolvedBy.POSITION_FILTER)
        }
        // still ambiguous: a stored mapping outside the current candidate set is stale, never a hit
        if (storedMappingMountPointId != null && filtered.any { it.mountPointId == storedMappingMountPointId }) {
            return SlotResolutionOutcome.Resolved(storedMappingMountPointId, ResolvedBy.SLOT_MAPPING)
        }
        if (userAnswerMountPointId != null && filtered.any { it.mountPointId == userAnswerMountPointId }) {
            return SlotResolutionOutcome.Resolved(userAnswerMountPointId, ResolvedBy.SLOT_MAPPING)
        }
        return SlotResolutionOutcome.Unresolved(filtered)
    }

    /** Injective resolution (ruling 3): slotIds whose resolved mount point collides with another slot's. */
    fun collidingSlotIds(resolvedMountPointBySlot: Map<UUID, UUID>): Set<UUID> =
        resolvedMountPointBySlot.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }
            .values
            .flatten()
            .toSet()
}
