package de.cyclingsir.cetrack.part.rest

import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.infrastructure.api.model.PartPartTypeRelation
import de.cyclingsir.cetrack.infrastructure.api.model.ReportItem
import de.cyclingsir.cetrack.infrastructure.api.rest.PartsApi
import de.cyclingsir.cetrack.part.domain.PartService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
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
    val mapper: PartApiMapper,
    val reportMapper: ReportDomain2ApiMapper
) : PartsApi {

//    @CrossOrigin
    override fun createPart(@Valid @RequestBody part: Part): ResponseEntity<Part> {
        val addedPart = service.addPart(mapper.map(part))
        return ResponseEntity.ok(mapper.map(addedPart))
    }

    override fun modifyPart(@PathVariable("partId") partId: UUID, @Valid @RequestBody part: Part): ResponseEntity<Part> {
        logger.debug {"API Part: $part"}
        val domainPart = mapper.map(part)
        logger.debug {"DomainPart: $domainPart"}
        val persistedPart = service.modifyPart(partId, domainPart)
        persistedPart?.apply {
            return ResponseEntity.ok(mapper.map(this))
        }
        return ResponseEntity.notFound().build()
    }

    override fun deletePart(@PathVariable("partId") partId: UUID) : ResponseEntity<Unit> {
        service.deletePart(partId)
        return ResponseEntity(HttpStatus.OK)
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

    override fun getReport(): ResponseEntity<List<ReportItem>> {
        val domainReport = service.getReport()
        val restReport = domainReport.map(reportMapper::map)
        return ResponseEntity.ok(restReport)
    }

    override fun relatePartToPartType(
        @PathVariable("partId") partId: UUID,
        @Valid @RequestBody partPartTypeRelation: PartPartTypeRelation
    ): ResponseEntity<Part> {
        val domainPart = service.createPartPartTypeRelation(mapper.map(partPartTypeRelation))
        return ResponseEntity.ok(mapper.map(domainPart))
    }

}
