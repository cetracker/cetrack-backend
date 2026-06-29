package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.infrastructure.api.rest.FitImportApi
import de.cyclingsir.cetrack.tour.domain.DomainTour
import de.cyclingsir.cetrack.tour.domain.FitImportService
import de.cyclingsir.cetrack.tour.domain.FitImportService.DraftWithHint
import de.cyclingsir.cetrack.tour.domain.TourSource
import de.cyclingsir.cetrack.tour.fit.FitParseException
import de.cyclingsir.cetrack.tour.storage.TourEntity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class FitImportControllerTest {

    private val fitImportService = mockk<FitImportService>()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(FitImportController(fitImportService, FitDraftMapper()))
        .setControllerAdvice(CentralExceptionHandler())
        .build()

    private fun aDraft(startedAt: Instant = Instant.parse("2022-07-17T19:35:11Z")) = DomainTour(
        id = null,
        mtTourId = null,
        title = "",
        distance = 2138,
        durationMoving = 570L,
        durationRecorded = 573L,
        durationElapsed = 627L,
        altUp = 33,
        altDown = 35,
        powerTotal = 0L,
        bike = null,
        startedAt = startedAt,
        startYear = 2022.toShort(),
        startMonth = 7.toShort(),
        startDay = 17.toShort(),
        createdAt = null,
        source = TourSource.FIT
    )

    @Test
    fun `parseFitFile returns 200 with draft tour list`() {
        every { fitImportService.parseToDrafts(any()) } returns listOf(
            DraftWithHint(aDraft(), emptyList())
        )

        mvc.perform(post(FitImportApi.PATH_PARSE_FIT_FILE)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(8)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].distance").value(2138))
            .andExpect(jsonPath("$[0].durationRecorded").value(573))
            .andExpect(jsonPath("$[0].durationElapsed").value(627))
            .andExpect(jsonPath("$[0].altUp").value(33))
            .andExpect(jsonPath("$[0].duplicateHint").doesNotExist())
    }

    @Test
    fun `parseFitFile returns duplicateHint when matching tours exist`() {
        val existingId = UUID.randomUUID()
        val matchingEntity = TourEntity(
            id = existingId,
            mtTourId = "9000000000001",
            title = "Prior Silverton Ride",
            distance = 2138,
            durationMoving = 570L,
            bike = BikeEntity(id = UUID.randomUUID(), model = "Road Bike"),
            startedAt = Instant.parse("2022-07-17T19:35:11Z"),
            startYear = 2022.toShort(),
            startMonth = 7.toShort(),
            startDay = 17.toShort(),
            altUp = 33,
            altDown = 35,
            powerTotal = 0L
        )
        every { fitImportService.parseToDrafts(any()) } returns listOf(
            DraftWithHint(aDraft(), listOf(matchingEntity))
        )

        mvc.perform(post(FitImportApi.PATH_PARSE_FIT_FILE)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(8)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].duplicateHint").exists())
            .andExpect(jsonPath("$[0].duplicateHint.matchedTours").isArray)
            .andExpect(jsonPath("$[0].duplicateHint.matchedTours[0].tourId").value(existingId.toString()))
            .andExpect(jsonPath("$[0].duplicateHint.matchedTours[0].title").value("Prior Silverton Ride"))
    }

    @Test
    fun `parseFitFile returns 400 on invalid FIT bytes`() {
        every { fitImportService.parseToDrafts(any()) } throws
            FitParseException("FIT file contains no sessions")

        mvc.perform(post(FitImportApi.PATH_PARSE_FIT_FILE)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(ByteArray(4) { 0xFF.toByte() }))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `parseFitFile parses real Silverton fixture`() {
        val bytes = FitImportControllerTest::class.java.classLoader
            .getResourceAsStream("fit-fixture/Bye_bye_Silverton.fit")!!
            .use { it.readBytes() }

        every { fitImportService.parseToDrafts(any()) } returns listOf(
            DraftWithHint(aDraft(), emptyList())
        )

        mvc.perform(post(FitImportApi.PATH_PARSE_FIT_FILE)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .content(bytes))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0]").exists())
    }
}
