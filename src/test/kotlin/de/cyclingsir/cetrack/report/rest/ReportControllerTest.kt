package de.cyclingsir.cetrack.report.rest

import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.report.domain.DomainTourReport
import de.cyclingsir.cetrack.report.domain.ReportService
import de.cyclingsir.cetrack.report.domain.TourGranularity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * yearsBack/endYear out-of-range 400s are NOT exercised here: they come from
 * the @Validated AOP proxy Spring Boot wraps around ReportController at
 * runtime (ConstraintViolationException via MethodValidationInterceptor,
 * verified against the real servlet stack), which standalone MockMvc's
 * bare `new ReportController(...)` never gets proxied into - see
 * ReportTourIT's HTTP-level cases for that coverage.
 */
class ReportControllerTest {

    private val service = mockk<ReportService>()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ReportController(service))
        .setControllerAdvice(CentralExceptionHandler())
        .build()

    private val emptyReport = DomainTourReport(availableYears = emptyList(), bikes = emptyList(), buckets = emptyList())

    @Test
    fun `unknown granularity yields 400 with code 901`() {
        mvc.perform(get("/reports/tours").param("granularity", "week"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("DATA_INVALID"))
    }

    @Test
    fun `no params calls service with defaults`() {
        every { service.tourReport(TourGranularity.MONTH, null, 1) } returns emptyReport

        mvc.perform(get("/reports/tours"))
            .andExpect(status().isOk)

        verify { service.tourReport(TourGranularity.MONTH, null, 1) }
    }

    @Test
    fun `year granularity response serializes month absent`() {
        every { service.tourReport(TourGranularity.YEAR, 2024, 1) } returns DomainTourReport(
            availableYears = listOf(2024),
            bikes = emptyList(),
            buckets = listOf(
                de.cyclingsir.cetrack.report.domain.DomainTourBucket(year = 2024, month = null, items = emptyList())
            )
        )

        mvc.perform(get("/reports/tours").param("granularity", "year").param("endYear", "2024"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.buckets[0].year").value(2024))
            .andExpect(jsonPath("$.buckets[0].month").doesNotExist())
    }
}
