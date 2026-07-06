package de.cyclingsir.cetrack.mounting

import de.cyclingsir.cetrack.bike.domain.BikeCompositionService
import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.domain.DomainMountPoint
import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

/**
 * GET /mountings is the §5 derived-view workhorse: activeAt = "bike at time X",
 * mountPointId = mount point history; rows are denormalized with bikeId +
 * mountPointName (single joined query).
 */
class MountingQueryIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService

    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val t3: Instant = Instant.parse("2024-03-01T00:00:00Z")

    @Test
    fun `bike at time X, mount point history, denormalized fields`() {
        val typeId = catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
        val front = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "front tire")
        ).id!!
        val rear = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "rear tire")
        ).id!!
        val tireA = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "tire A")).id!!
        val tireB = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "tire B")).id!!

        mountingService.mount(bikeId, front, tireA, t1)
        mountingService.mount(bikeId, rear, tireB, t1)
        mountingService.mount(bikeId, front, tireB, t3) // moves B rear -> front, closes A

        // bike at t2: A on front, B on rear
        val atT2 = mountingService.getMountings(null, null, bikeId, t2)
        assertThat(atT2).hasSize(2)
        assertThat(atT2.map { it.componentId to it.mountPointId })
            .containsExactlyInAnyOrder(tireA to front, tireB to rear)
        assertThat(atT2).allSatisfy {
            assertThat(it.bikeId).isEqualTo(bikeId)
            assertThat(it.mountPointName).isNotBlank()
        }

        // bike now: only B on front
        val now = mountingService.getMountings(null, null, bikeId, Instant.parse("2024-04-01T00:00:00Z"))
        assertThat(now.map { it.componentId to it.mountPointId }).containsExactly(tireB to front)

        // mount point history of front, ordered by time
        val history = mountingService.getMountings(null, front, null, null)
        assertThat(history.map { it.componentId }).containsExactly(tireA, tireB)
        assertThat(history.first().dismountedAt).isEqualTo(t3)
        assertThat(history.first().mountPointName).isEqualTo("front tire")
    }
}
