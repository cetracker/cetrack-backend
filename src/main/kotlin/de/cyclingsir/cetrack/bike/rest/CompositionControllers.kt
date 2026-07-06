package de.cyclingsir.cetrack.bike.rest

import de.cyclingsir.cetrack.bike.domain.BikeCompositionService
import de.cyclingsir.cetrack.bike.domain.DomainMountPoint
import de.cyclingsir.cetrack.infrastructure.api.model.MountPoint
import de.cyclingsir.cetrack.infrastructure.api.model.MountPointInput
import de.cyclingsir.cetrack.infrastructure.api.model.MountRequest
import de.cyclingsir.cetrack.infrastructure.api.model.MountingChanges
import de.cyclingsir.cetrack.infrastructure.api.model.SlotMapping
import de.cyclingsir.cetrack.infrastructure.api.rest.MountPointsApi
import de.cyclingsir.cetrack.infrastructure.api.rest.SlotMappingsApi
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.ZoneOffset
import java.util.UUID

/**
 * The generated MountPointsApi is tag-granular, so the mount action lives on
 * this controller and delegates to mounting/'s domain service (accepted
 * rest->domain edge, see issues/plans/CE-0083.md module cut).
 */
@RestController
class MountPointController(
    private val compositionService: BikeCompositionService,
    private val mountingService: MountingService,
    private val mountingMapper: MountingDomain2ApiMapper,
) : MountPointsApi {

    override fun getMountPoints(@PathVariable("bikeId") bikeId: UUID): ResponseEntity<List<MountPoint>> =
        ResponseEntity.ok(compositionService.getMountPoints(bikeId).map(::toApi))

    override fun createMountPoint(
        @PathVariable("bikeId") bikeId: UUID,
        @Valid @RequestBody mountPointInput: MountPointInput
    ): ResponseEntity<MountPoint> {
        val added = compositionService.addMountPoint(toDomain(bikeId, mountPointInput))
        return ResponseEntity
            .created(URI.create("/api/bikes/$bikeId/mountPoints/${added.id}"))
            .body(toApi(added))
    }

    override fun modifyMountPoint(
        @PathVariable("bikeId") bikeId: UUID,
        @PathVariable("mountPointId") mountPointId: UUID,
        @Valid @RequestBody mountPointInput: MountPointInput
    ): ResponseEntity<MountPoint> =
        ResponseEntity.ok(
            toApi(compositionService.modifyMountPoint(bikeId, mountPointId, toDomain(bikeId, mountPointInput)))
        )

    override fun deleteMountPoint(
        @PathVariable("bikeId") bikeId: UUID,
        @PathVariable("mountPointId") mountPointId: UUID
    ): ResponseEntity<Unit> {
        compositionService.deleteMountPoint(bikeId, mountPointId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun mountComponent(
        @PathVariable("bikeId") bikeId: UUID,
        @PathVariable("mountPointId") mountPointId: UUID,
        @Valid @RequestBody mountRequest: MountRequest
    ): ResponseEntity<MountingChanges> =
        ResponseEntity.ok(
            mountingMapper.map(
                mountingService.mount(bikeId, mountPointId, mountRequest.componentId, mountRequest.at.toInstant())
            )
        )

    private fun toDomain(bikeId: UUID, input: MountPointInput) = DomainMountPoint(
        bikeId = bikeId,
        componentTypeId = input.componentTypeId,
        positionId = input.positionId,
        name = input.name,
        mandatory = input.mandatory
    )

    private fun toApi(domain: DomainMountPoint) = MountPoint(
        id = domain.id,
        bikeId = domain.bikeId,
        componentTypeId = domain.componentTypeId,
        positionId = domain.positionId,
        name = domain.name,
        mandatory = domain.mandatory,
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )
}

@RestController
class SlotMappingController(
    private val compositionService: BikeCompositionService,
) : SlotMappingsApi {

    override fun getSlotMappings(@PathVariable("bikeId") bikeId: UUID): ResponseEntity<List<SlotMapping>> =
        ResponseEntity.ok(
            compositionService.getSlotMappings(bikeId).map {
                SlotMapping(
                    id = it.id!!,
                    assemblySlotId = it.assemblySlotId,
                    bikeId = it.bikeId,
                    mountPointId = it.mountPointId,
                    createdAt = it.createdAt?.atOffset(ZoneOffset.UTC)
                )
            }
        )

    override fun deleteSlotMapping(
        @PathVariable("bikeId") bikeId: UUID,
        @PathVariable("slotMappingId") slotMappingId: UUID
    ): ResponseEntity<Unit> {
        compositionService.deleteSlotMapping(bikeId, slotMappingId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
