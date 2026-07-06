package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.infrastructure.api.model.TourCreateRequest
import de.cyclingsir.cetrack.tour.domain.DomainTour
import de.cyclingsir.cetrack.tour.domain.TourService
import de.cyclingsir.cetrack.tour.domain.TourSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class TourControllerTest {

    private val service = mockk<TourService>()
    private val tourMapper = mockk<TourDomain2ApiMapper>()
    private val importMapper = mockk<MTTourDomain2ApiMapper>()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(TourController(service, tourMapper, importMapper))
        .setControllerAdvice(CentralExceptionHandler())
        .build()

    private fun stubAddTour(source: TourSource = TourSource.MANUAL) {
        val domainSlot = slot<DomainTour>()
        val sourceSlot = slot<TourSource>()
        val apiTour = de.cyclingsir.cetrack.infrastructure.api.model.Tour(
            id = java.util.UUID.randomUUID(),
            title = "Test",
            distance = 30000,
            durationMoving = 3600,
            ascent = 200,
            descent = 200,
            powerTotal = 0,
            startedAt = java.time.OffsetDateTime.now(),
            startYear = 2024,
            startMonth = 6,
            startDay = 1
        )
        every { tourMapper.map(capture(domainSlot)) } returns apiTour
        every { service.addTour(any(), capture(sourceSlot)) } answers {
            domainSlot.captured.copy(source = sourceSlot.captured)
        }
    }

    private val validCreateBody = """
        {
          "title": "Test Tour",
          "distance": 30000,
          "durationMoving": 3600,
          "durationRecorded": 3600,
          "durationElapsed": 4000,
          "ascent": 200,
          "descent": 200,
          "powerTotal": 0,
          "startedAt": "2024-06-01T08:00:00Z",
          "startYear": 2024,
          "startMonth": 6,
          "startDay": 1,
          "bike": { "id": "a1111111-0001-0001-0001-000000000001", "model": "Road Bike" }
        }
    """.trimIndent()

    @Test
    fun `createTour with source=MYTOURBOOK returns 400 (server-only value rejected by Jackson)`() {
        val body = validCreateBody.trimEnd('}') + """, "source": "MYTOURBOOK" }"""

        mvc.perform(post("/tours")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `createTour without source defaults to MANUAL`() {
        val sourceSlot = slot<TourSource>()
        stubAddTour()
        every { tourMapper.map(any<TourCreateRequest>()) } returns mockk(relaxed = true)
        every { service.addTour(any(), capture(sourceSlot)) } answers {
            mockk(relaxed = true)
        }

        mvc.perform(post("/tours")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validCreateBody))
            .andExpect(status().isOk)

        assertEquals(TourSource.MANUAL, sourceSlot.captured)
    }

    @Test
    fun `createTour with source=FIT passes FIT to addTour`() {
        val sourceSlot = slot<TourSource>()
        stubAddTour()
        every { tourMapper.map(any<TourCreateRequest>()) } returns mockk(relaxed = true)
        every { service.addTour(any(), capture(sourceSlot)) } answers {
            mockk(relaxed = true)
        }

        val body = validCreateBody.trimEnd('}') + """, "source": "FIT" }"""
        mvc.perform(post("/tours")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk)

        assertEquals(TourSource.FIT, sourceSlot.captured)
    }
}
