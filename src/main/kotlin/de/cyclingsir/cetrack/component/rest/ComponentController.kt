package de.cyclingsir.cetrack.component.rest

import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainRetirementKind
import de.cyclingsir.cetrack.infrastructure.api.model.Component
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentInput
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentStatus
import de.cyclingsir.cetrack.infrastructure.api.model.DismountRequest
import de.cyclingsir.cetrack.infrastructure.api.model.MountingChanges
import de.cyclingsir.cetrack.infrastructure.api.model.RetireComponentRequest
import de.cyclingsir.cetrack.infrastructure.api.rest.ComponentsApi
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class ComponentController(
    private val service: ComponentService,
    private val mapper: ComponentDomain2ApiMapper,
    private val mountingService: MountingService,
    private val mountingMapper: MountingDomain2ApiMapper,
) : ComponentsApi {

    override fun dismountComponent(
        @PathVariable("componentId") componentId: UUID,
        @Valid @RequestBody dismountRequest: DismountRequest
    ): ResponseEntity<MountingChanges> =
        ResponseEntity.ok(
            mountingMapper.map(mountingService.dismount(componentId, dismountRequest.at.toInstant()))
        )

    override fun getComponents(
        @Valid @RequestParam(value = "componentTypeId", required = false) componentTypeId: UUID?,
        @Valid @RequestParam(value = "status", required = false) status: ComponentStatus?
    ): ResponseEntity<List<Component>> =
        ResponseEntity.ok(
            service.getComponents(componentTypeId, status?.let(mapper::map)).map(mapper::map)
        )

    override fun getComponent(@PathVariable("componentId") componentId: UUID): ResponseEntity<Component> =
        ResponseEntity.ok(mapper.map(service.getComponent(componentId)))

    override fun createComponent(
        @Valid @RequestBody componentInput: ComponentInput
    ): ResponseEntity<Component> {
        val added = service.addComponent(mapper.map(componentInput))
        return ResponseEntity
            .created(URI.create("/api/components/${added.id}"))
            .body(mapper.map(added))
    }

    override fun modifyComponent(
        @PathVariable("componentId") componentId: UUID,
        @Valid @RequestBody componentInput: ComponentInput
    ): ResponseEntity<Component> =
        ResponseEntity.ok(mapper.map(service.modifyComponent(componentId, mapper.map(componentInput))))

    override fun deleteComponent(@PathVariable("componentId") componentId: UUID): ResponseEntity<Unit> {
        service.deleteComponent(componentId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun retireComponent(
        @PathVariable("componentId") componentId: UUID,
        @Valid @RequestBody retireComponentRequest: RetireComponentRequest
    ): ResponseEntity<Component> {
        val kind = when (retireComponentRequest.kind) {
            RetireComponentRequest.Kind.scrapped -> DomainRetirementKind.SCRAPPED
            RetireComponentRequest.Kind.sold -> DomainRetirementKind.SOLD
        }
        val retired = service.retireComponent(componentId, retireComponentRequest.at.toInstant(), kind)
        return ResponseEntity.ok(mapper.map(retired))
    }
}
