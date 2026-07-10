package de.cyclingsir.cetrack.assembly.rest

import de.cyclingsir.cetrack.assembly.domain.AssemblyMountingService
import de.cyclingsir.cetrack.assembly.domain.AssemblyService
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.AddMemberRequest
import de.cyclingsir.cetrack.infrastructure.api.model.Assembly
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyInput
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyMembership
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyMountResult
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblyMounting
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblySlot
import de.cyclingsir.cetrack.infrastructure.api.model.AssemblySlotInput
import de.cyclingsir.cetrack.infrastructure.api.model.DismountAssemblyRequest
import de.cyclingsir.cetrack.infrastructure.api.model.MountAssemblyRequest
import de.cyclingsir.cetrack.infrastructure.api.model.MountPlan
import de.cyclingsir.cetrack.infrastructure.api.model.MountingChanges
import de.cyclingsir.cetrack.infrastructure.api.model.PlanMountRequest
import de.cyclingsir.cetrack.infrastructure.api.model.RemoveMemberRequest
import de.cyclingsir.cetrack.infrastructure.api.rest.AssembliesApi
import de.cyclingsir.cetrack.infrastructure.api.rest.MembershipsApi
import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@RestController
class AssemblyController(
    private val service: AssemblyService,
    private val mountingService: AssemblyMountingService,
    private val mapper: AssemblyDomain2ApiMapper,
    private val mountingMapper: MountingDomain2ApiMapper,
) : AssembliesApi, MembershipsApi {

    override fun getAssemblies(): ResponseEntity<List<Assembly>> =
        ResponseEntity.ok(service.getAssemblies().map(mapper::map))

    override fun getAssembly(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestParam(value = "at", required = false) at: OffsetDateTime?
    ): ResponseEntity<Assembly> =
        ResponseEntity.ok(mapper.map(service.getAssembly(assemblyId, at?.toInstant() ?: Instant.now())))

    override fun createAssembly(@Valid @RequestBody assemblyInput: AssemblyInput): ResponseEntity<Assembly> {
        val created = service.createAssembly(mapper.map(assemblyInput))
        return ResponseEntity.created(URI.create("/api/assemblies/${created.id}")).body(mapper.map(created))
    }

    override fun modifyAssembly(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody assemblyInput: AssemblyInput
    ): ResponseEntity<Assembly> =
        ResponseEntity.ok(mapper.map(service.modifyAssembly(assemblyId, mapper.map(assemblyInput))))

    override fun deleteAssembly(@PathVariable("assemblyId") assemblyId: UUID): ResponseEntity<Unit> {
        service.deleteAssembly(assemblyId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun createAssemblySlot(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody assemblySlotInput: AssemblySlotInput
    ): ResponseEntity<AssemblySlot> {
        val created = service.createAssemblySlot(assemblyId, mapper.map(assemblySlotInput, assemblyId))
        return ResponseEntity
            .created(URI.create("/api/assemblies/$assemblyId/slots/${created.id}"))
            .body(mapper.map(created))
    }

    override fun modifyAssemblySlot(
        @PathVariable("assemblyId") assemblyId: UUID,
        @PathVariable("slotId") slotId: UUID,
        @Valid @RequestBody assemblySlotInput: AssemblySlotInput
    ): ResponseEntity<AssemblySlot> =
        ResponseEntity.ok(
            mapper.map(service.modifyAssemblySlot(assemblyId, slotId, mapper.map(assemblySlotInput, assemblyId)))
        )

    override fun deleteAssemblySlot(
        @PathVariable("assemblyId") assemblyId: UUID,
        @PathVariable("slotId") slotId: UUID
    ): ResponseEntity<Unit> {
        service.deleteAssemblySlot(assemblyId, slotId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun getAssemblyMountings(@PathVariable("assemblyId") assemblyId: UUID): ResponseEntity<List<AssemblyMounting>> =
        ResponseEntity.ok(service.getAssemblyMountings(assemblyId).map(mapper::map))

    override fun planMountAssembly(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody planMountRequest: PlanMountRequest
    ): ResponseEntity<MountPlan> =
        ResponseEntity.ok(
            mapper.map(mountingService.planMount(assemblyId, planMountRequest.bikeId, planMountRequest.at.toInstant()))
        )

    override fun mountAssembly(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody mountAssemblyRequest: MountAssemblyRequest
    ): ResponseEntity<AssemblyMountResult> =
        ResponseEntity.ok(
            mapper.map(
                mountingService.mountAssembly(
                    assemblyId, mountAssemblyRequest.bikeId, mountAssemblyRequest.at.toInstant(),
                    mountAssemblyRequest.slotResolutions.orEmpty().map(mapper::map)
                )
            )
        )

    override fun dismountAssembly(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody dismountAssemblyRequest: DismountAssemblyRequest
    ): ResponseEntity<AssemblyMountResult> =
        ResponseEntity.ok(mapper.map(mountingService.dismountAssembly(assemblyId, dismountAssemblyRequest.at.toInstant())))

    override fun addAssemblyMember(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody addMemberRequest: AddMemberRequest
    ): ResponseEntity<MountingChanges> =
        ResponseEntity.ok(
            mountingMapper.map(
                mountingService.addMember(
                    assemblyId, addMemberRequest.componentId, addMemberRequest.slotId,
                    addMemberRequest.from.toInstant(), addMemberRequest.mountPointId
                )
            )
        )

    override fun removeAssemblyMember(
        @PathVariable("assemblyId") assemblyId: UUID,
        @Valid @RequestBody removeMemberRequest: RemoveMemberRequest
    ): ResponseEntity<MountingChanges> =
        ResponseEntity.ok(
            mountingMapper.map(mountingService.removeMember(removeMemberRequest.componentId, removeMemberRequest.to.toInstant()))
        )

    override fun getMemberships(
        @Valid @RequestParam(value = "slotId", required = false) slotId: UUID?,
        @Valid @RequestParam(value = "componentId", required = false) componentId: UUID?,
        @Valid @RequestParam(value = "activeAt", required = false) activeAt: OffsetDateTime?
    ): ResponseEntity<List<AssemblyMembership>> =
        ResponseEntity.ok(service.getMemberships(slotId, componentId, activeAt?.toInstant()).map(mapper::map))

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun correctMembership(
        @PathVariable("membershipId") membershipId: UUID,
        @Valid @RequestBody correctMembershipRequest: CorrectMembershipRequest
    ): ResponseEntity<AssemblyMembership> {
        if (correctMembershipRequest.memberFromPresent && correctMembershipRequest.memberFrom == null) {
            throw ServiceException(ErrorCodesDomain.CORRECTION_INVALID,
                "memberFrom cannot be null - a membership always has a from time.")
        }
        return ResponseEntity.ok(
            mapper.map(
                mountingService.correctMembership(
                    membershipId,
                    correctMembershipRequest.memberFrom?.toInstant(),
                    correctMembershipRequest.memberTo?.toInstant(),
                    reopen = correctMembershipRequest.memberToPresent
                        && correctMembershipRequest.memberTo == null
                )
            )
        )
    }

    override fun voidMembership(@PathVariable("membershipId") membershipId: UUID): ResponseEntity<Unit> {
        mountingService.voidMembership(membershipId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
