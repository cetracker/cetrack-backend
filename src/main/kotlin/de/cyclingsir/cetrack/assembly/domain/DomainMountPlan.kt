package de.cyclingsir.cetrack.assembly.domain

import de.cyclingsir.cetrack.mounting.domain.DomainMountingChanges
import de.cyclingsir.cetrack.bike.domain.DomainSlotMapping
import java.time.Instant
import java.util.UUID

enum class DomainPlannedSlotState { RESOLVED, UNRESOLVED, IMPOSSIBLE, EMPTY }

enum class DomainImpossibleReason { NO_CANDIDATE, POSITION_FILTER_EMPTY, MEMBER_MOUNTED_ELSEWHERE }

data class DomainCandidate(val mountPointId: UUID, val mountPointName: String, val positionId: UUID?)

data class DomainPlannedSlot(
    val slotId: UUID,
    val componentId: UUID? = null,
    val state: DomainPlannedSlotState,
    val mountPointId: UUID? = null,
    val resolvedBy: ResolvedBy? = null,
    val willDismountComponentId: UUID? = null,
    val willDismountAssemblyMountingId: UUID? = null,
    val candidates: List<DomainCandidate> = emptyList(),
    val reasonCode: DomainImpossibleReason? = null,
    val reason: String? = null,
)

/** Another assembly whose governed mounting occupies a target point; overriding it dismounts it as a unit. */
data class DomainAssemblyToDismount(val assemblyId: UUID, val assemblyMountingId: UUID, val name: String)

data class DomainMountPlan(
    val assemblyId: UUID,
    val bikeId: UUID,
    val at: Instant,
    val mountable: Boolean,
    val slots: List<DomainPlannedSlot>,
    val assembliesToDismount: List<DomainAssemblyToDismount> = emptyList(),
)

/** User answer for one slot the plan reported unresolved (persisted as a SlotMapping). */
data class DomainSlotResolution(val slotId: UUID, val mountPointId: UUID)

data class DomainAssemblyMountResult(
    val assemblyMounting: DomainAssemblyMounting,
    val changes: DomainMountingChanges,
    val rememberedSlotMappings: List<DomainSlotMapping> = emptyList(),
    val dismountedAssemblyMountings: List<DomainAssemblyMounting> = emptyList(),
)
