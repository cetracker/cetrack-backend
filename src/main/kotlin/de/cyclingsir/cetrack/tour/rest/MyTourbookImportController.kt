package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.infrastructure.api.model.CommitImportRequest
import de.cyclingsir.cetrack.infrastructure.api.model.ImportSession
import de.cyclingsir.cetrack.infrastructure.api.rest.MyTourbookImportApi
import de.cyclingsir.cetrack.tour.domain.MyTourbookImportService
import de.cyclingsir.cetrack.tour.domain.WarningResolutionRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class MyTourbookImportController(
    private val importService: MyTourbookImportService,
    private val mapper: ImportSessionMapper
) {

    @PostMapping(
        MyTourbookImportApi.PATH_STAGE_MY_TOURBOOK_IMPORT,
        consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun stage(request: HttpServletRequest): ResponseEntity<ImportSession> {
        val session = importService.stage(request.inputStream)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.map(session))
    }

    @GetMapping(
        MyTourbookImportApi.PATH_GET_PENDING_MY_TOURBOOK_IMPORT_SESSION,
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getPendingSession(): ResponseEntity<ImportSession> {
        val session = importService.getPendingSession() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(mapper.map(session))
    }

    @GetMapping(
        MyTourbookImportApi.PATH_GET_MY_TOURBOOK_IMPORT_SESSION,
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getSession(@PathVariable sessionId: UUID): ResponseEntity<ImportSession> {
        val session = importService.getSession(sessionId)
        return ResponseEntity.ok(mapper.map(session))
    }

    @PostMapping(
        MyTourbookImportApi.PATH_COMMIT_MY_TOURBOOK_IMPORT,
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun commit(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: CommitImportRequest
    ): ResponseEntity<Unit> {
        val resolutions = request.warningResolutions.orEmpty()
            .map { WarningResolutionRequest(it.mtTourId, it.action.value) }
        importService.commit(sessionId, request.approvedMtTourIds.toList(), resolutions)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
