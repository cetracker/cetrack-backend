package de.cyclingsir.cetrack.mounting

import de.cyclingsir.cetrack.bike.domain.BikeCompositionService
import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.domain.DomainMountPoint
import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The <=1-active invariants (domain-model.md §4 traceability table): service
 * paths answer 409, and the DB exclusion constraints / partial unique indexes
 * hold even against raw SQL (the concurrent-safety net).
 */
class MountingInvariantIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val t3: Instant = Instant.parse("2024-03-01T00:00:00Z")

    private data class Fixture(val bikeId: UUID, val mountPointId: UUID, val componentId: UUID, val typeId: UUID)

    private fun fixture(): Fixture {
        val typeId = catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
        val mountPointId = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp")
        ).id!!
        val componentId = componentService.addComponent(
            DomainComponent(componentTypeId = typeId, label = "comp-${UUID.randomUUID()}")
        ).id!!
        return Fixture(bikeId, mountPointId, componentId, typeId)
    }

    @Test
    fun `service-level overlap re-validation answers 409`() {
        val f = fixture()
        val second = componentService.addComponent(
            DomainComponent(componentTypeId = f.typeId, label = "second")
        ).id!!
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t1)
        mountingService.mount(f.bikeId, f.mountPointId, second, t2)
        val first = mountingService.getMountings(f.componentId, null, null, null).single()

        // stretching the closed first mounting over the active second one
        val ex = assertThrows<ServiceException> {
            mountingService.correct(first.id, null, t3)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
    }

    @Test
    fun `exclusion constraint rejects overlapping intervals per mount point via raw SQL`() {
        val f = fixture()
        val other = componentService.addComponent(
            DomainComponent(componentTypeId = f.typeId, label = "other")
        ).id!!
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?)",
            f.componentId, f.mountPointId, Timestamp.from(t1), Timestamp.from(t3)
        )
        val ex = assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
                other, f.mountPointId, Timestamp.from(t2)
            )
        }
        assertThat(ex.message).containsIgnoringCase("exclusion")
    }

    @Test
    fun `exclusion constraint rejects overlapping intervals per component via raw SQL`() {
        val f = fixture()
        val otherMountPoint = compositionService.addMountPoint(
            DomainMountPoint(bikeId = f.bikeId, componentTypeId = f.typeId, name = "mp2")
        ).id!!
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?)",
            f.componentId, f.mountPointId, Timestamp.from(t1), Timestamp.from(t3)
        )
        val ex = assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
                f.componentId, otherMountPoint, Timestamp.from(t2)
            )
        }
        assertThat(ex.message).containsIgnoringCase("exclusion")
    }

    @Test
    fun `partial unique index caps one active mounting per mount point`() {
        val f = fixture()
        val other = componentService.addComponent(
            DomainComponent(componentTypeId = f.typeId, label = "other")
        ).id!!
        // non-overlapping open interval after a closed one is fine
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?)",
            f.componentId, f.mountPointId, Timestamp.from(t1), Timestamp.from(t2)
        )
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
            other, f.mountPointId, Timestamp.from(t2)
        )
        // a second active row on the same mount point violates the invariant
        val third = componentService.addComponent(
            DomainComponent(componentTypeId = f.typeId, label = "third")
        ).id!!
        assertThrows<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
                third, f.mountPointId, Timestamp.from(t3)
            )
        }
    }
}
