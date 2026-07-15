package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.FitDraftTour
import de.cyclingsir.cetrack.infrastructure.api.rest.FitImportApi
import de.cyclingsir.cetrack.tour.domain.FitImportService
import de.cyclingsir.cetrack.tour.fit.FitParseException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
class FitImportController(
    private val fitImportService: FitImportService,
    private val mapper: FitDraftMapper
) {

    @PostMapping(
        FitImportApi.PATH_PARSE_FIT_FILE,
        consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun parseFitFile(request: HttpServletRequest): ResponseEntity<List<FitDraftTour>> {
        return try {
            val drafts = fitImportService.parseToDrafts(request.inputStream)
            ResponseEntity.ok(drafts.map(mapper::map))
        } catch (e: FitParseException) {
            logger.warn { "FIT parse failed: ${e.message}" }
            throw ServiceException(ErrorCodesDomain.FIT_PARSE_FAILED, e.message)
        }
    }
}
