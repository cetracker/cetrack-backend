package de.cyclingsir.cetrack.part.rest

import de.cyclingsir.cetrack.infrastructure.api.model.PartType
import de.cyclingsir.cetrack.infrastructure.api.rest.PartTypesApi
import de.cyclingsir.cetrack.part.domain.PartTypeService
import jakarta.validation.Valid
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
    val mapper: PartTypeDomain2ApiMapper
) : PartTypesApi {

    override fun createPartType(@Valid @RequestBody partType: PartType): ResponseEntity<PartType> {
        val addedPartType = service.addPartType(mapper.map(partType))
        return ResponseEntity.ok(mapper.map(addedPartType))
    }

    override fun getPartTypes(): ResponseEntity<List<PartType>> {
        val domainPartTypes = service.getPartTypes()
        val restPartTypes = domainPartTypes.map(mapper::map)
        return ResponseEntity.ok(restPartTypes);
    }

    override fun relatePartTypeToBike(
        @PathVariable("partTypeId") partTypeId: UUID,
        @Valid @RequestParam(value = "bikeId", required = true) bikeId: UUID
    ): ResponseEntity<PartType> {
        val modifiedPartType = service.relatePartTypeToBike(partTypeId, bikeId)
        return ResponseEntity.ok(mapper.map(modifiedPartType))
    }
}
