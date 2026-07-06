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
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class MountingCorrectionIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var wac: WebApplicationContext

    // real context converters: proves the wire-level absent-vs-null distinction
    private val mvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(wac).build() }

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
    fun `correct adjusts times and re-validates the interval`() {
        val f = fixture()
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t2)
        val mounting = mountingService.getMountings(f.componentId, null, null, null).single()

        val corrected = mountingService.correct(mounting.id, t1, null)
        assertThat(corrected.mountedAt).isEqualTo(t1)
        assertThat(corrected.dismountedAt).isNull()
        assertThat(corrected.mountPointName).isNotBlank()

        val closed = mountingService.correct(mounting.id, null, t3)
        assertThat(closed.dismountedAt).isEqualTo(t3)

        val exEmpty = assertThrows<ServiceException> { mountingService.correct(mounting.id, null, null) }
        assertThat(exEmpty.getError()).isEqualTo(ErrorCodesDomain.CORRECTION_INVALID)

        val exNegative = assertThrows<ServiceException> { mountingService.correct(mounting.id, t3, t1) }
        assertThat(exNegative.getError()).isEqualTo(ErrorCodesDomain.CORRECTION_INVALID)
    }

    @Test
    fun `correct rejects overlaps with other mountings of the same component`() {
        val f = fixture()
        val otherMountPoint = compositionService.addMountPoint(
            DomainMountPoint(bikeId = f.bikeId, componentTypeId = f.typeId, name = "mp2")
        ).id!!
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t1)
        mountingService.mount(f.bikeId, otherMountPoint, f.componentId, t2) // closes the first at t2
        val first = mountingService.getMountings(f.componentId, f.mountPointId, null, null).single()

        val ex = assertThrows<ServiceException> { mountingService.correct(first.id, null, t3) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
    }

    @Test
    fun `correct re-opens a closed mounting on explicit re-open`() {
        val f = fixture()
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t1)
        mountingService.dismount(f.componentId, t2)
        val mounting = mountingService.getMountings(f.componentId, null, null, null).single()

        val reopened = mountingService.correct(mounting.id, null, null, reopenDismount = true)

        assertThat(reopened.dismountedAt).isNull()
        assertThat(mountingService.getMountings(f.componentId, null, null, t3).single().id)
            .isEqualTo(mounting.id)
    }

    @Test
    fun `re-open is rejected when the open-ended interval would overlap`() {
        val f = fixture()
        val otherMountPoint = compositionService.addMountPoint(
            DomainMountPoint(bikeId = f.bikeId, componentTypeId = f.typeId, name = "mp2")
        ).id!!
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t1)
        mountingService.mount(f.bikeId, otherMountPoint, f.componentId, t2) // closes the first at t2
        val first = mountingService.getMountings(f.componentId, f.mountPointId, null, null).single()

        val ex = assertThrows<ServiceException> {
            mountingService.correct(first.id, null, null, reopenDismount = true)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
    }

    @Test
    fun `wire tri-state - omitted keeps, explicit null re-opens, null mountedAt is 400`() {
        val f = fixture()
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t1)
        mountingService.dismount(f.componentId, t2)
        val id = mountingService.getMountings(f.componentId, null, null, null).single().id
        val path = "/mountings/$id/action/correct"

        // omitted dismountedAt keeps the current value
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"mountedAt":"2024-01-15T00:00:00Z"}"""))
            .andExpect(status().isOk)
        assertThat(mountingService.getMounting(id).dismountedAt).isEqualTo(t2)

        // explicit null dismountedAt re-opens
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"dismountedAt":null}"""))
            .andExpect(status().isOk)
        assertThat(mountingService.getMounting(id).dismountedAt).isNull()

        // explicit null mountedAt is invalid
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"mountedAt":null}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CORRECTION_INVALID"))
    }

    @Test
    fun `governed mountings can be neither corrected nor voided`() {
        val f = fixture()
        val assemblyId = UUID.randomUUID()
        val assemblyMountingId = UUID.randomUUID()
        jdbc.update("INSERT INTO component_assembly (id, name) VALUES (?, ?)", assemblyId, "assembly")
        jdbc.update(
            "INSERT INTO assembly_mounting (id, assembly_id, bike_id, mounted_at) VALUES (?, ?, ?, ?)",
            assemblyMountingId, assemblyId, f.bikeId, Timestamp.from(t1)
        )
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, assembly_mounting_id, mounted_at) VALUES (?, ?, ?, ?)",
            f.componentId, f.mountPointId, assemblyMountingId, Timestamp.from(t1)
        )
        val governed = mountingService.getMountings(f.componentId, null, null, null).single()

        val exCorrect = assertThrows<ServiceException> { mountingService.correct(governed.id, t2, null) }
        assertThat(exCorrect.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_GOVERNED)

        val exVoid = assertThrows<ServiceException> { mountingService.void(governed.id) }
        assertThat(exVoid.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_GOVERNED)
    }

    @Test
    fun `void erases the erratum`() {
        val f = fixture()
        mountingService.mount(f.bikeId, f.mountPointId, f.componentId, t1)
        val mounting = mountingService.getMountings(f.componentId, null, null, null).single()

        mountingService.void(mounting.id)

        assertThat(mountingService.getMountings(f.componentId, null, null, null)).isEmpty()
        val exGone = assertThrows<ServiceException> { mountingService.getMounting(mounting.id) }
        assertThat(exGone.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_NOT_FOUND)
    }
}
