package de.cyclingsir.cetrack.part.rest

import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.infrastructure.api.model.PartPartTypeRelation
import de.cyclingsir.cetrack.infrastructure.api.rest.PartsApi
import de.cyclingsir.cetrack.part.domain.PartService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Initially created on 12/17/22.
 */
private val logger = KotlinLogging.logger {}

@RestController
class PartController(
    val service: PartService,
    val partMapper: PartDomain2ApiMapper,
    val relationMapper: PartPartTypeRelationDomain2ApiMapper
) : PartsApi {

//    @CrossOrigin
    override fun createPart(@Valid @RequestBody part: Part): ResponseEntity<Part> {
        val addedPart = service.addPart(partMapper.map(part))
        return ResponseEntity.ok(partMapper.map(addedPart))
    }

    override fun modifyPart(@PathVariable("partId") partId: UUID, @Valid @RequestBody part: Part): ResponseEntity<Part> {
        logger.debug("API Part: $part")
        val domainPart = partMapper.map(part)
        logger.debug("DomainPart: $domainPart")
        val persistedPart = service.modifyPart(partId, domainPart)
        persistedPart?.apply {
            return ResponseEntity.ok(partMapper.map(persistedPart))
        }
        return ResponseEntity.notFound().build()
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

    override fun getReport(): ResponseEntity<Unit> {
        service.getReport()
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun relatePartToPartType(
        @PathVariable("partId") partId: UUID,
        @Valid @RequestBody partPartTypeRelation: PartPartTypeRelation
    ): ResponseEntity<Part> {
        val domainPart = service.createPartPartTypeRelation(relationMapper.map(partPartTypeRelation))
        return ResponseEntity.ok(partMapper.map(domainPart))
    }

}
