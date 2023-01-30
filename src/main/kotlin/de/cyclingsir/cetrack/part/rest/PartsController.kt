package de.cyclingsir.cetrack.part.rest

import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.infrastructure.api.model.PartPartTypeRelation
import de.cyclingsir.cetrack.infrastructure.api.rest.PartsApi
import de.cyclingsir.cetrack.part.domain.PartsService
import jakarta.validation.Valid
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * Initially created on 12/17/22.
 */
@RestController
class PartsController(
    val service: PartsService,
    val mapper: PartDomain2ApiMapper
) : PartsApi {

    override fun createPart(@Valid @RequestBody part: Part): ResponseEntity<Part> {
        val addedPart = service.addPart(mapper.map(part))
        return ResponseEntity.ok(mapper.map(addedPart))
    }



    override fun getPart(partId: UUID): ResponseEntity<Part> {
        val part = service.getPart(partId)
        part?.apply {
            return ResponseEntity.ok(mapper.map(part))
        }
        return ResponseEntity.notFound().build()
    }

    override fun getParts(): ResponseEntity<List<Part>> {
        val domainParts = service.getParts()
        val restParts = domainParts.map(mapper::map)
        return ResponseEntity.ok(restParts);
    }

    override fun relatePartToPartType(partId: UUID, partPartTypeRelation: PartPartTypeRelation): ResponseEntity<Part> {
        return super.relatePartToPartType(partId, partPartTypeRelation)
    }
}
