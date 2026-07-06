package de.cyclingsir.cetrack.assembly.rest

import de.cyclingsir.cetrack.assembly.domain.DomainAssembly
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblyMounting
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblyMountResult
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblySlot
import de.cyclingsir.cetrack.assembly.domain.DomainCandidate
import de.cyclingsir.cetrack.assembly.domain.DomainImpossibleReason
import de.cyclingsir.cetrack.assembly.domain.DomainMountPlan
import de.cyclingsir.cetrack.assembly.domain.DomainPlannedSlot
import de.cyclingsir.cetrack.assembly.domain.DomainPlannedSlotState
import de.cyclingsir.cetrack.assembly.domain.DomainSlotResolution
import de.cyclingsir.cetrack.assembly.domain.ResolvedBy
import de.cyclingsir.cetrack.bike.domain.DomainSlotMapping
import de.cyclingsir.cetrack.infrastructure.api.model.Assembly
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyInput
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyMountResult
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyMounting
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblySlot
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblySlotInput
import de.cyclingsir.cetrack.infrastructure.api.model.Candidate
import de.cyclingsir.cetrack.infrastructure.api.model.MountPlan
import de.cyclingsir.cetrack.infrastructure.api.model.PlannedSlot
import de.cyclingsir.cetrack.infrastructure.api.model.SlotMapping
import de.cyclingsir.cetrack.infrastructure.api.model.SlotResolution
import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import java.time.ZoneOffset

class AssemblyDomain2ApiMapper(
    private val mountingMapper: MountingDomain2ApiMapper,
) {

    fun map(domain: DomainAssembly): Assembly = Assembly(
        id = domain.id,
        name = domain.name,
        positionId = domain.positionId,
        complete = domain.complete,
        mounted = domain.mounted,
        slots = domain.slots.map(::map),
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(rest: AssemblyInput): DomainAssembly = DomainAssembly(name = rest.name, positionId = rest.positionId)

    fun map(domain: DomainAssemblySlot): AssemblySlot = AssemblySlot(
        id = domain.id,
        name = domain.name,
        componentTypeId = domain.componentTypeId,
        validFrom = domain.validFrom.atOffset(ZoneOffset.UTC),
        validTo = domain.validTo?.atOffset(ZoneOffset.UTC),
        memberComponentId = domain.memberComponentId,
        memberFrom = domain.memberFrom?.atOffset(ZoneOffset.UTC),
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(rest: AssemblySlotInput, assemblyId: java.util.UUID): DomainAssemblySlot = DomainAssemblySlot(
        assemblyId = assemblyId,
        name = rest.name,
        componentTypeId = rest.componentTypeId,
        validFrom = rest.validFrom.toInstant(),
        validTo = rest.validTo?.toInstant()
    )

    fun map(domain: DomainAssemblyMounting): AssemblyMounting = AssemblyMounting(
        id = domain.id!!,
        assemblyId = domain.assemblyId,
        bikeId = domain.bikeId,
        mountedAt = domain.mountedAt.atOffset(ZoneOffset.UTC),
        dismountedAt = domain.dismountedAt?.atOffset(ZoneOffset.UTC),
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(domain: DomainSlotMapping): SlotMapping = SlotMapping(
        id = domain.id!!,
        assemblySlotId = domain.assemblySlotId,
        bikeId = domain.bikeId,
        mountPointId = domain.mountPointId,
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(rest: SlotResolution): DomainSlotResolution = DomainSlotResolution(rest.slotId, rest.mountPointId)

    fun map(domain: DomainMountPlan): MountPlan = MountPlan(
        assemblyId = domain.assemblyId,
        bikeId = domain.bikeId,
        at = domain.at.atOffset(ZoneOffset.UTC),
        mountable = domain.mountable,
        slots = domain.slots.map(::map)
    )

    fun map(domain: DomainPlannedSlot): PlannedSlot = PlannedSlot(
        slotId = domain.slotId,
        state = when (domain.state) {
            DomainPlannedSlotState.RESOLVED -> PlannedSlot.State.resolved
            DomainPlannedSlotState.UNRESOLVED -> PlannedSlot.State.unresolved
            DomainPlannedSlotState.IMPOSSIBLE -> PlannedSlot.State.impossible
            DomainPlannedSlotState.EMPTY -> PlannedSlot.State.empty
        },
        componentId = domain.componentId,
        mountPointId = domain.mountPointId,
        resolvedBy = domain.resolvedBy?.let {
            when (it) {
                ResolvedBy.UNIQUE_CANDIDATE -> PlannedSlot.ResolvedBy.uniqueCandidate
                ResolvedBy.POSITION_FILTER -> PlannedSlot.ResolvedBy.positionFilter
                ResolvedBy.SLOT_MAPPING -> PlannedSlot.ResolvedBy.slotMapping
            }
        },
        willDismountComponentId = domain.willDismountComponentId,
        candidates = domain.candidates.map(::map),
        reasonCode = domain.reasonCode?.let {
            when (it) {
                DomainImpossibleReason.NO_CANDIDATE -> PlannedSlot.ReasonCode.noCandidate
                DomainImpossibleReason.POSITION_FILTER_EMPTY -> PlannedSlot.ReasonCode.positionFilterEmpty
                DomainImpossibleReason.MEMBER_MOUNTED_ELSEWHERE -> PlannedSlot.ReasonCode.memberMountedElsewhere
                DomainImpossibleReason.OCCUPIED_BY_GOVERNED_MOUNTING -> PlannedSlot.ReasonCode.occupiedByGovernedMounting
            }
        },
        reason = domain.reason
    )

    fun map(domain: DomainCandidate): Candidate = Candidate(
        mountPointId = domain.mountPointId,
        mountPointName = domain.mountPointName,
        positionId = domain.positionId
    )

    fun map(domain: DomainAssemblyMountResult): AssemblyMountResult = AssemblyMountResult(
        assemblyMounting = map(domain.assemblyMounting),
        changes = mountingMapper.map(domain.changes),
        rememberedSlotMappings = domain.rememberedSlotMappings.map(::map)
    )

}
