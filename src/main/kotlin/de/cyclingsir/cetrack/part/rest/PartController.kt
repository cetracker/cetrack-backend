package de.cyclingsir.cetrack.part.rest

import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.infrastructure.api.model.PartPartTypeRelation
import de.cyclingsir.cetrack.infrastructure.api.rest.PartsApi
import de.cyclingsir.cetrack.part.domain.PartService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * Initially created on 12/17/22.
 */
@RestController
class PartController(
    val service: PartService,
    val partMapper: PartDomain2ApiMapper,
    val relationMapper: PartPartTypeRelationDomain2ApiMapper
) : PartsApi {

    override fun createPart(@Valid @RequestBody part: Part): ResponseEntity<Part> {
        val addedPart = service.addPart(partMapper.map(part))
        return ResponseEntity.ok(partMapper.map(addedPart))
    }


    override fun getPart(partId: UUID): ResponseEntity<Part> {
        val part = service.getPart(partId)
        part?.apply {
            return ResponseEntity.ok(partMapper.map(part))
        }
        return ResponseEntity.notFound().build()
    }

    override fun getParts(): ResponseEntity<List<Part>> {
        val domainParts = service.getParts()
        val restParts = domainParts.map(partMapper::map)
        return ResponseEntity.ok(restParts);
    }

    override fun relatePartToPartType(
        @Parameter(required = true) @PathVariable("partId") partId: UUID,
        @Parameter(required = true) @Valid @RequestBody partPartTypeRelation: PartPartTypeRelation
    ): ResponseEntity<Part> {
        val domainPart = service.createPartPartTypeRelation(relationMapper.map(partPartTypeRelation))
        return ResponseEntity.ok(partMapper.map(domainPart))
    }

}
