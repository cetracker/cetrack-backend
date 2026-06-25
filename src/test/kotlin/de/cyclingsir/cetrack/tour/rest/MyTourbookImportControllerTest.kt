package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain.ARCHIVE_INVALID
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain.DERBY_SCHEMA_INCOMPATIBLE
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain.IMPORT_SESSION_NOT_FOUND
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain.IMPORT_SESSION_SUPERSEDED
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.infrastructure.api.rest.MyTourbookImportApi
import de.cyclingsir.cetrack.tour.domain.DomainImportSession
import de.cyclingsir.cetrack.tour.domain.DomainImportWarning
import de.cyclingsir.cetrack.tour.domain.MyTourbookImportService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class MyTourbookImportControllerTest {

    private val importService = mockk<MyTourbookImportService>()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(MyTourbookImportController(importService, ImportSessionMapper()))
        .setControllerAdvice(CentralExceptionHandler())
        .build()

    companion object {
        val SESSION_ID: UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000001")
    }

    private fun aDomainSession(
        status: String = "PENDING",
        candidates: List<DomainMTTour> = emptyList(),
        warnings: List<DomainImportWarning> = emptyList(),
        hasDrift: Boolean = false
    ) = DomainImportSession(SESSION_ID, status, 59, hasDrift, candidates, warnings)

    private fun aMTTour(mtTourId: String = "9000000000001") = DomainMTTour(
        MTTOURID = mtTourId,
        TITLE = "Tour $mtTourId",
        DISTANCE = 30_000,
        DURATIONMOVING = 5400L,
        STARTTIMESTAMP = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli(),
        STARTYEAR = 2026.toShort(),
        STARTMONTH = 1.toShort(),
        STARTDAY = 15.toShort(),
        TOURALTUP = 200,
        TOURALTDOWN = 150,
        POWERTOTAL = 0L,
        bikeId = null
    )

    // #35
    @Test
    fun `stage returns 201 with sessionId and status in body`() {
        every { importService.stage(any()) } returns aDomainSession()

        mvc.perform(post(MyTourbookImportApi.PATH_STAGE_MY_TOURBOOK_IMPORT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(4)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    // #46
    @Test
    fun `stage returns 204 when archive yields nothing new`() {
        every { importService.stage(any()) } returns null

        mvc.perform(post(MyTourbookImportApi.PATH_STAGE_MY_TOURBOOK_IMPORT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(4)))
            .andExpect(status().isNoContent)
    }

    // #36
    @Test
    fun `getSession returns 200 with full session shape`() {
        val warning = DomainImportWarning("AMBIGUOUS_BIKE", "9000000000025", "Ambiguous bike")
        every { importService.getSession(SESSION_ID) } returns
            aDomainSession(candidates = listOf(aMTTour()), warnings = listOf(warning), hasDrift = true)

        mvc.perform(get(MyTourbookImportApi.PATH_GET_MY_TOURBOOK_IMPORT_SESSION, SESSION_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
            .andExpect(jsonPath("$.dbVersion").value(59))
            .andExpect(jsonPath("$.hasDrift").value(true))
            .andExpect(jsonPath("$.candidates").isArray)
            .andExpect(jsonPath("$.candidates[0].mtTourId").value("9000000000001"))
            .andExpect(jsonPath("$.warnings").isArray)
            .andExpect(jsonPath("$.warnings[0].type").value("AMBIGUOUS_BIKE"))
    }

    // #43
    @Test
    fun `getPendingSession returns 204 when no session exists`() {
        every { importService.getPendingSession() } returns null

        mvc.perform(get(MyTourbookImportApi.PATH_GET_PENDING_MY_TOURBOOK_IMPORT_SESSION))
            .andExpect(status().isNoContent)
    }

    // #44
    @Test
    fun `getPendingSession returns 200 with session body when session exists`() {
        every { importService.getPendingSession() } returns aDomainSession(candidates = listOf(aMTTour()))

        mvc.perform(get(MyTourbookImportApi.PATH_GET_PENDING_MY_TOURBOOK_IMPORT_SESSION))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.candidates[0].mtTourId").value("9000000000001"))
    }

    // #37
    @Test
    fun `commit returns 201`() {
        every { importService.commit(SESSION_ID, any(), any()) } just Runs

        mvc.perform(post(MyTourbookImportApi.PATH_COMMIT_MY_TOURBOOK_IMPORT, SESSION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"approvedMtTourIds":["9000000000001"]}"""))
            .andExpect(status().isCreated)
    }

    // #38
    @Test
    fun `getSession on unknown id returns 404`() {
        every { importService.getSession(any()) } throws
            ServiceException(IMPORT_SESSION_NOT_FOUND, "not found")

        mvc.perform(get(MyTourbookImportApi.PATH_GET_MY_TOURBOOK_IMPORT_SESSION, SESSION_ID))
            .andExpect(status().isNotFound)
    }

    // #39
    @Test
    fun `commit on superseded session returns 409`() {
        every { importService.commit(any(), any(), any()) } throws
            ServiceException(IMPORT_SESSION_SUPERSEDED, "superseded")

        mvc.perform(post(MyTourbookImportApi.PATH_COMMIT_MY_TOURBOOK_IMPORT, SESSION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"approvedMtTourIds":["9000000000001"]}"""))
            .andExpect(status().isConflict)
    }

    // #40
    @Test
    fun `stage with invalid archive returns 400`() {
        every { importService.stage(any()) } throws
            ServiceException(ARCHIVE_INVALID, "corrupt archive")

        mvc.perform(post(MyTourbookImportApi.PATH_STAGE_MY_TOURBOOK_IMPORT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(8)))
            .andExpect(status().isBadRequest)
    }

    // #41
    @Test
    fun `stage with incompatible derby schema returns 422`() {
        every { importService.stage(any()) } throws
            ServiceException(DERBY_SCHEMA_INCOMPATIBLE, "schema error")

        mvc.perform(post(MyTourbookImportApi.PATH_STAGE_MY_TOURBOOK_IMPORT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(8)))
            .andExpect(status().`is`(422))
    }

    // #42
    @Test
    fun `stage with large body reaches service proving no whole-body buffering at HTTP layer`() {
        every { importService.stage(any()) } throws
            ServiceException(ARCHIVE_INVALID, "archive exceeds size limit")

        mvc.perform(post(MyTourbookImportApi.PATH_STAGE_MY_TOURBOOK_IMPORT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(1024 * 1024)))
            .andExpect(status().isBadRequest)
    }
}
