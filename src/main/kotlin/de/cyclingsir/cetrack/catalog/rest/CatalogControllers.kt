package de.cyclingsir.cetrack.catalog.rest

import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentType
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentTypeInput
import de.cyclingsir.cetrack.infrastructure.api.model.Position
import de.cyclingsir.cetrack.infrastructure.api.model.PositionInput
import de.cyclingsir.cetrack.infrastructure.api.rest.ComponentTypesApi
import de.cyclingsir.cetrack.infrastructure.api.rest.PositionsApi
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class ComponentTypeController(
    private val service: CatalogService,
    private val mapper: CatalogDomain2ApiMapper,
) : ComponentTypesApi {

    override fun getComponentTypes(): ResponseEntity<List<ComponentType>> =
        ResponseEntity.ok(service.getComponentTypes().map(mapper::map))

    override fun getComponentType(@PathVariable("componentTypeId") componentTypeId: UUID): ResponseEntity<ComponentType> =
        ResponseEntity.ok(mapper.map(service.getComponentType(componentTypeId)))

    override fun createComponentType(
        @Valid @RequestBody componentTypeInput: ComponentTypeInput
    ): ResponseEntity<ComponentType> {
        val added = service.addComponentType(mapper.map(componentTypeInput))
        return ResponseEntity
            .created(URI.create("/api/componentTypes/${added.id}"))
            .body(mapper.map(added))
    }

    override fun modifyComponentType(
        @PathVariable("componentTypeId") componentTypeId: UUID,
        @Valid @RequestBody componentTypeInput: ComponentTypeInput
    ): ResponseEntity<ComponentType> =
        ResponseEntity.ok(mapper.map(service.modifyComponentType(componentTypeId, mapper.map(componentTypeInput))))

    override fun deleteComponentType(@PathVariable("componentTypeId") componentTypeId: UUID): ResponseEntity<Unit> {
        service.deleteComponentType(componentTypeId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}

@RestController
class PositionController(
    private val service: CatalogService,
    private val mapper: CatalogDomain2ApiMapper,
) : PositionsApi {

    override fun getPositions(): ResponseEntity<List<Position>> =
        ResponseEntity.ok(service.getPositions().map(mapper::map))

    override fun getPosition(@PathVariable("positionId") positionId: UUID): ResponseEntity<Position> =
        ResponseEntity.ok(mapper.map(service.getPosition(positionId)))

    override fun createPosition(
        @Valid @RequestBody positionInput: PositionInput
    ): ResponseEntity<Position> {
        val added = service.addPosition(mapper.map(positionInput))
        return ResponseEntity
            .created(URI.create("/api/positions/${added.id}"))
            .body(mapper.map(added))
    }

    override fun modifyPosition(
        @PathVariable("positionId") positionId: UUID,
        @Valid @RequestBody positionInput: PositionInput
    ): ResponseEntity<Position> =
        ResponseEntity.ok(mapper.map(service.modifyPosition(positionId, mapper.map(positionInput))))

    override fun deletePosition(@PathVariable("positionId") positionId: UUID): ResponseEntity<Unit> {
        service.deletePosition(positionId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
