package de.cyclingsir.cetrack.part.rest

import de.cyclingsir.cetrack.infrastructure.api.model.PartType
import de.cyclingsir.cetrack.infrastructure.api.rest.PartTypesApi
import de.cyclingsir.cetrack.part.domain.PartTypeService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Initially created on 1/31/23.
 */
@RestController
class PartTypeController(
    val service: PartTypeService,
    val mapper: PartApiMapper
) : PartTypesApi {

    override fun createPartType(@Valid @RequestBody partType: PartType): ResponseEntity<PartType> {
        val addedPartType = service.addPartType(mapper.map(partType))
        return ResponseEntity.ok(mapper.map(addedPartType))
    }

    override fun modifyPartType(@PathVariable("partTypeId") partTypeId: UUID, @Valid @RequestBody partType: PartType): ResponseEntity<PartType> {
        val persistedPartType = service.modifyPartType(partTypeId, mapper.map(partType))
        persistedPartType?.apply {
            return ResponseEntity.ok(mapper.map(this))
        }
        return ResponseEntity.notFound().build()
    }

    override fun deletePartType(partTypeId: UUID): ResponseEntity<Unit> {
        service.deletePartType(partTypeId)
        return ResponseEntity(HttpStatus.OK)
    }


    override fun getPartTypes(): ResponseEntity<List<PartType>> {
        val domainPartTypes = service.getPartTypes()
        val restPartTypes = domainPartTypes.map(mapper::map)
        return ResponseEntity.ok(restPartTypes);
    }

    override fun relatePartTypeToBike(
        @Parameter(required = true) @PathVariable("partTypeId") partTypeId: java.util.UUID,
        @NotNull @Parameter(required = true)
        @Valid @RequestParam(value = "bikeId", required = true) bikeId: java.util.UUID
    ): ResponseEntity<PartType> {
        val modifiedPartType = service.relatePartTypeToBike(partTypeId, bikeId)
        return ResponseEntity.ok(mapper.map(modifiedPartType))
    }
}
