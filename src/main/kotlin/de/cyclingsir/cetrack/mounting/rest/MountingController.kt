package de.cyclingsir.cetrack.mounting.rest

import de.cyclingsir.cetrack.infrastructure.api.model.CorrectMountingRequest
import de.cyclingsir.cetrack.infrastructure.api.model.Mounting
import de.cyclingsir.cetrack.infrastructure.api.rest.MountingsApi
import de.cyclingsir.cetrack.mounting.domain.MountingService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

@RestController
class MountingController(
    private val service: MountingService,
    private val mapper: MountingDomain2ApiMapper,
) : MountingsApi {

    override fun getMountings(
        @Valid @RequestParam(value = "componentId", required = false) componentId: UUID?,
        @Valid @RequestParam(value = "mountPointId", required = false) mountPointId: UUID?,
        @Valid @RequestParam(value = "bikeId", required = false) bikeId: UUID?,
        @Valid @RequestParam(value = "activeAt", required = false) activeAt: OffsetDateTime?
    ): ResponseEntity<List<Mounting>> =
        ResponseEntity.ok(
            service.getMountings(componentId, mountPointId, bikeId, activeAt?.toInstant()).map(mapper::map)
        )

    override fun getMounting(@PathVariable("mountingId") mountingId: UUID): ResponseEntity<Mounting> =
        ResponseEntity.ok(mapper.map(service.getMounting(mountingId)))

    override fun correctMounting(
        @PathVariable("mountingId") mountingId: UUID,
        @Valid @RequestBody correctMountingRequest: CorrectMountingRequest
    ): ResponseEntity<Mounting> =
        ResponseEntity.ok(
            mapper.map(
                service.correct(
                    mountingId,
                    correctMountingRequest.mountedAt?.toInstant(),
                    correctMountingRequest.dismountedAt?.toInstant()
                )
            )
        )

    override fun voidMounting(@PathVariable("mountingId") mountingId: UUID): ResponseEntity<Unit> {
        service.void(mountingId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
