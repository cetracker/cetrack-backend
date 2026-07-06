package de.cyclingsir.cetrack.report

import de.cyclingsir.cetrack.bike.domain.BikeCompositionService
import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.domain.DomainMountPoint
import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.report.domain.MileageScope
import de.cyclingsir.cetrack.report.domain.ReportService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ReportMileageIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var reportService: ReportService
    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val jan1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val feb1: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val mar1: Instant = Instant.parse("2024-03-01T00:00:00Z")

    private fun seedTour(bikeId: UUID, startedAt: Instant, distance: Int, moving: Long,
                         ascent: Int = 100, descent: Int = 90, power: Long = 1000L,
                         elapsed: Long = moving) {
        jdbc.update(
            """INSERT INTO tour (bike_id, title, started_at, start_year, start_month, start_day,
                                 duration_moving, duration_recorded, duration_elapsed,
                                 distance, ascent, descent, power_total)
               VALUES (?, ?, ?, 2024, 1, 1, ?, ?, ?, ?, ?, ?, ?)""",
            bikeId, "tour", Timestamp.from(startedAt), moving, elapsed, elapsed, distance, ascent, descent, power
        )
    }

    @Test
    fun `sums covered tours per component, respects scope and time frame`() {
        val typeId = catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!
        val bikeId = bikeService.addBike(DomainBike(name = "Report bike")).id!!
        val mountPointId = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp")
        ).id!!
        val compA = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "A")).id!!
        val compB = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "B")).id!!

        mountingService.mount(bikeId, mountPointId, compA, jan1)   // A: [jan1, feb1)
        mountingService.mount(bikeId, mountPointId, compB, feb1)   // B: [feb1, ...)

        seedTour(bikeId, jan1.plus(Duration.ofDays(5)), distance = 10_000, moving = 3600L)   // A
        seedTour(bikeId, jan1.plus(Duration.ofDays(10)), distance = 20_000, moving = 7200L)  // A
        seedTour(bikeId, feb1.plus(Duration.ofDays(5)), distance = 40_000, moving = 10_000L) // B

        val components = reportService.mileage(MileageScope.COMPONENTS, null, null, null, null)
        val rowA = components.single { it.componentId == compA }
        val rowB = components.single { it.componentId == compB }
        assertThat(rowA.distance).isEqualTo(30_000L)
        assertThat(rowA.durationMoving).isEqualTo(10_800L)
        assertThat(rowA.ascent).isEqualTo(200L)
        assertThat(rowA.descent).isEqualTo(180L)
        assertThat(rowA.powerTotal).isEqualTo(2000L)
        assertThat(rowB.distance).isEqualTo(40_000L)

        // time frame: only A's second tour
        val january10on = reportService.mileage(
            MileageScope.COMPONENTS, null, null, jan1.plus(Duration.ofDays(9)), jan1.plus(Duration.ofDays(20))
        )
        assertThat(january10on.single { it.componentId == compA }.distance).isEqualTo(20_000L)

        // single component filter
        val onlyB = reportService.mileage(MileageScope.COMPONENTS, compB, null, null, null)
        assertThat(onlyB).singleElement().extracting { it.componentId }.isEqualTo(compB)

        // scope=bikes: totals over all three tours regardless of mounting
        val bikes = reportService.mileage(MileageScope.BIKES, null, null, null, null)
        val bikeRow = bikes.single { it.bikeId == bikeId }
        assertThat(bikeRow.distance).isEqualTo(70_000L)
        assertThat(bikeRow.bikeName).isEqualTo("Report bike")

        // scope=bikes + componentId: A's per-bike breakdown
        val bikeForA = reportService.mileage(MileageScope.BIKES, compA, null, null, null)
        assertThat(bikeForA.single { it.bikeId == bikeId }.distance).isEqualTo(30_000L)
    }

    @Test
    fun `a mid-tour swap counts the tour for neither component`() {
        val typeId = catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!
        val bikeId = bikeService.addBike(DomainBike(model = "Swap bike")).id!!
        val mountPointId = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp")
        ).id!!
        val before = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "before")).id!!
        val after = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "after")).id!!

        mountingService.mount(bikeId, mountPointId, before, jan1)
        // 2h tour starting 1h before the swap at mar1
        seedTour(bikeId, mar1.minus(Duration.ofHours(1)), distance = 30_000, moving = 7200L)
        mountingService.mount(bikeId, mountPointId, after, mar1)

        val rows = reportService.mileage(MileageScope.COMPONENTS, null, bikeId, null, null)
        assertThat(rows).isEmpty()

        // boundary: a tour ending exactly at the swap instant is covered by the closed mounting
        seedTour(bikeId, mar1.minus(Duration.ofHours(2)), distance = 5_000, moving = 7200L)
        val rows2 = reportService.mileage(MileageScope.COMPONENTS, null, bikeId, null, null)
        assertThat(rows2.single().componentId).isEqualTo(before)
        assertThat(rows2.single().distance).isEqualTo(5_000L)
    }
}
