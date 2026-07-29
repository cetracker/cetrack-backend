package de.cyclingsir.cetrack.report

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.report.domain.ReportService
import de.cyclingsir.cetrack.report.domain.TourGranularity
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * The Testcontainers container is static and shared with no cleanup, and
 * other IT classes seed tours (e.g. ReportMileageIT hardcodes 2024-01-01) -
 * availableYears()/the endYear default are global queries, so this class
 * must own its own isolation.
 */
class ReportTourIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var reportService: ReportService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    // ReportsApi is @Validated, so Spring Boot proxies ReportController with a
    // MethodValidationInterceptor that raises ConstraintViolationException for
    // out-of-range endYear/yearsBack before the controller method runs - that
    // AOP proxy only exists in the real ApplicationContext, not standalone
    // MockMvc (see ReportControllerTest), hence the HTTP-level cases live here.
    private val mvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    @BeforeEach
    fun clearTours() {
        jdbc.update("DELETE FROM tour")
    }

    private fun seedTour(
        bikeId: UUID?,
        day: LocalDate,
        distance: Int,
        moving: Long,
        ascent: Int = 0
    ) {
        val startedAt = day.atStartOfDay(ZoneOffset.UTC).toInstant()
        jdbc.update(
            """INSERT INTO tour (bike_id, title, started_at, start_year, start_month, start_day,
                                 duration_moving, duration_recorded, duration_elapsed,
                                 distance, ascent, descent, power_total)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)""",
            bikeId, "tour", Timestamp.from(startedAt),
            day.year, day.monthValue, day.dayOfMonth,
            moving, moving, moving, distance, ascent
        )
    }

    @Test
    fun `two bikes in one month yield one bucket with two items, empty buckets omitted`() {
        val bikeA = bikeService.addBike(DomainBike(name = "A")).id!!
        val bikeB = bikeService.addBike(DomainBike(name = "B")).id!!
        seedTour(bikeA, LocalDate.of(2024, 3, 5), distance = 10_000, moving = 3600L)
        seedTour(bikeB, LocalDate.of(2024, 3, 15), distance = 20_000, moving = 7200L)

        val report = reportService.tourReport(TourGranularity.MONTH, endYear = 2024, yearsBack = 1)

        assertThat(report.buckets).singleElement().satisfies({ bucket ->
            assertThat(bucket.year).isEqualTo(2024)
            assertThat(bucket.month).isEqualTo(3)
            assertThat(bucket.items).hasSize(2)
            assertThat(bucket.items.single { it.bikeId == bikeA }.distance).isEqualTo(10_000L)
            assertThat(bucket.items.single { it.bikeId == bikeB }.distance).isEqualTo(20_000L)
        })
    }

    @Test
    fun `year granularity collapses months`() {
        val bikeId = bikeService.addBike(DomainBike(name = "A")).id!!
        seedTour(bikeId, LocalDate.of(2024, 1, 5), distance = 10_000, moving = 3600L)
        seedTour(bikeId, LocalDate.of(2024, 6, 5), distance = 20_000, moving = 3600L)

        val report = reportService.tourReport(TourGranularity.YEAR, endYear = 2024, yearsBack = 1)

        assertThat(report.buckets).singleElement().satisfies({ bucket ->
            assertThat(bucket.month).isNull()
            assertThat(bucket.items.single().distance).isEqualTo(30_000L)
        })
    }

    @Test
    fun `range filter restricts to endYear and yearsBack`() {
        val bikeId = bikeService.addBike(DomainBike(name = "A")).id!!
        seedTour(bikeId, LocalDate.of(2022, 1, 5), distance = 1_000, moving = 100L)
        seedTour(bikeId, LocalDate.of(2023, 1, 5), distance = 2_000, moving = 100L)
        seedTour(bikeId, LocalDate.of(2024, 1, 5), distance = 4_000, moving = 100L)
        seedTour(bikeId, LocalDate.of(2025, 1, 5), distance = 8_000, moving = 100L)

        val report = reportService.tourReport(TourGranularity.YEAR, endYear = 2024, yearsBack = 2)

        assertThat(report.buckets).extracting<Int> { it.year }.containsExactly(2023, 2024)
    }

    @Test
    fun `availableYears ignores range and is newest first`() {
        val bikeId = bikeService.addBike(DomainBike(name = "A")).id!!
        seedTour(bikeId, LocalDate.of(2022, 1, 5), distance = 1_000, moving = 100L)
        seedTour(bikeId, LocalDate.of(2025, 1, 5), distance = 1_000, moving = 100L)

        val report = reportService.tourReport(TourGranularity.YEAR, endYear = 2022, yearsBack = 1)

        assertThat(report.availableYears).containsExactly(2025, 2022)
    }

    @Test
    fun `empty range yields empty buckets but populated availableYears`() {
        val bikeId = bikeService.addBike(DomainBike(name = "A")).id!!
        seedTour(bikeId, LocalDate.of(2024, 1, 5), distance = 1_000, moving = 100L)

        val report = reportService.tourReport(TourGranularity.YEAR, endYear = 2020, yearsBack = 1)

        assertThat(report.buckets).isEmpty()
        assertThat(report.availableYears).containsExactly(2024)
    }

    @Test
    fun `omitted endYear defaults to the newest tour year`() {
        val bikeId = bikeService.addBike(DomainBike(name = "A")).id!!
        seedTour(bikeId, LocalDate.of(2023, 1, 5), distance = 1_000, moving = 100L)
        seedTour(bikeId, LocalDate.of(2025, 1, 5), distance = 2_000, moving = 100L)

        val report = reportService.tourReport(TourGranularity.YEAR, endYear = null, yearsBack = 1)

        assertThat(report.buckets).singleElement().extracting { it.year }.isEqualTo(2025)
    }

    @Test
    fun `bikes list is ordered by display identity and excludes bikes without tours in range`() {
        val bikeNamed = bikeService.addBike(DomainBike(name = "Zeta")).id!!
        val bikeModelOnly = bikeService.addBike(DomainBike(model = "Alpha-model")).id!!
        val bikeOutOfRange = bikeService.addBike(DomainBike(name = "OutOfRange")).id!!
        seedTour(bikeNamed, LocalDate.of(2024, 1, 5), distance = 1_000, moving = 100L)
        seedTour(bikeModelOnly, LocalDate.of(2024, 2, 5), distance = 1_000, moving = 100L)
        seedTour(bikeOutOfRange, LocalDate.of(2020, 1, 5), distance = 1_000, moving = 100L)

        val report = reportService.tourReport(TourGranularity.YEAR, endYear = 2024, yearsBack = 1)

        assertThat(report.bikes).extracting<UUID> { it.bikeId }.containsExactly(bikeModelOnly, bikeNamed)
    }

    @Test
    fun `yearsBack over the spec max yields 400 with the shared Error body`() {
        mvc.perform(get("/reports/tours").param("yearsBack", "999"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("DATA_INVALID"))
    }

    @Test
    fun `endYear below the spec min yields 400 with the shared Error body`() {
        mvc.perform(get("/reports/tours").param("endYear", "1200"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("DATA_INVALID"))
    }
}
