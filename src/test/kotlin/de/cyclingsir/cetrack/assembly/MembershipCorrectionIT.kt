package de.cyclingsir.cetrack.assembly

import de.cyclingsir.cetrack.assembly.domain.AssemblyMountingService
import de.cyclingsir.cetrack.assembly.domain.AssemblyService
import de.cyclingsir.cetrack.assembly.domain.DomainAssembly
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblySlot
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
import java.time.Instant
import java.util.UUID

class MembershipCorrectionIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var assemblyService: AssemblyService
    @Autowired private lateinit var mountingAssemblyService: AssemblyMountingService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var wac: WebApplicationContext

    // real context converters: proves the wire-level absent-vs-null distinction
    private val mvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(wac).build() }

    private val t0: Instant = Instant.parse("2023-12-01T00:00:00Z")
    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t15: Instant = Instant.parse("2024-01-15T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val t3: Instant = Instant.parse("2024-03-01T00:00:00Z")

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    private fun newBike(): UUID = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!

    private fun newMountPoint(bikeId: UUID, typeId: UUID, name: String = "mp-${UUID.randomUUID()}"): UUID =
        compositionService.addMountPoint(DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = name)).id!!

    private fun newComponent(typeId: UUID, label: String = "comp-${UUID.randomUUID()}"): UUID =
        componentService.addComponent(DomainComponent(componentTypeId = typeId, label = label)).id!!

    /** One assembly, one slot, one member component active from t1 - the common fixture. */
    private data class Fixture(val assemblyId: UUID, val slotId: UUID, val typeId: UUID, val componentId: UUID)

    private fun fixture(): Fixture {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "asm-${UUID.randomUUID()}")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val componentId = newComponent(typeId)
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            componentId, slotId, java.sql.Timestamp.from(t1)
        )
        return Fixture(assemblyId, slotId, typeId, componentId)
    }

    private fun membershipId(slotId: UUID, componentId: UUID? = null): UUID =
        assemblyService.getMemberships(slotId, componentId, null)
            .first { componentId == null || it.componentId == componentId }.id

    // --- cascade rule ------------------------------------------------------------------------

    @Test
    fun `coincident boundary cascades - member joined an already-mounted assembly`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val secondType = newType()
        val secondMountPoint = newMountPoint(bikeId, secondType)
        val secondSlot = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "second", componentTypeId = secondType, validFrom = t1)
        ).id!!
        val secondComponent = newComponent(secondType)
        mountingAssemblyService.addMember(f.assemblyId, secondComponent, secondSlot, t2, mountPointId = null)
        val id = membershipId(secondSlot, secondComponent)
        // by construction addMember-while-mounted stamps the governed mounting's mountedAt == from
        assertThat(mountingService.getMountings(secondComponent, null, null, null).single().mountedAt).isEqualTo(t2)

        val corrected = mountingAssemblyService.correctMembership(id, t15, null)
        assertThat(corrected.memberFrom).isEqualTo(t15)
        val mounting = mountingService.getMountings(secondComponent, secondMountPoint, null, null).single()
        assertThat(mounting.mountedAt).isEqualTo(t15)
    }

    @Test
    fun `non-coincident boundary does not cascade - assembly mounted later`() {
        val f = fixture() // memberFrom = t1
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        // assembly mounted at t2, not at the member's memberFrom (t1) - the governed mounting's
        // mountedAt is the assembly-mount time, independent of the membership boundary
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        val id = membershipId(f.slotId, f.componentId)
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().mountedAt).isEqualTo(t2)

        val corrected = mountingAssemblyService.correctMembership(id, t0, null)
        assertThat(corrected.memberFrom).isEqualTo(t0)
        // untouched: still t2, not cascaded to t0
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().mountedAt).isEqualTo(t2)
    }

    // --- overlap rejection ---------------------------------------------------------------------

    @Test
    fun `correct rejects an overlap with another membership of the same slot`() {
        val f = fixture()
        val newMember = newComponent(f.typeId)
        mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t2, null) // swaps: f.componentId closed [t1,t2)
        val activeId = membershipId(f.slotId, newMember)

        val ex = assertThrows<ServiceException> { mountingAssemblyService.correctMembership(activeId, t1, null) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBERSHIP_OVERLAP)
    }

    @Test
    fun `correct rejects an overlap with another membership of the same component`() {
        val f = fixture() // component active [t1, null) in f.slotId
        mountingAssemblyService.removeMember(f.componentId, t2) // now closed [t1,t2)

        val elsewhereAssembly = assemblyService.createAssembly(DomainAssembly(name = "elsewhere")).id!!
        val elsewhereSlot = assemblyService.createAssemblySlot(
            elsewhereAssembly, DomainAssemblySlot(assemblyId = elsewhereAssembly, name = "slot", componentTypeId = f.typeId, validFrom = t1)
        ).id!!
        mountingAssemblyService.addMember(elsewhereAssembly, f.componentId, elsewhereSlot, t3, null)
        val id = membershipId(elsewhereSlot, f.componentId)

        // moving memberFrom back into [t1,t2) overlaps the closed membership of the same component
        val ex = assertThrows<ServiceException> { mountingAssemblyService.correctMembership(id, t15, null) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBERSHIP_OVERLAP)
    }

    // --- re-open ---------------------------------------------------------------------------

    @Test
    fun `memberTo explicit null re-opens a closed membership`() {
        val f = fixture()
        mountingAssemblyService.removeMember(f.componentId, t2)
        val id = membershipId(f.slotId, f.componentId)

        val reopened = mountingAssemblyService.correctMembership(id, null, null, reopen = true)

        assertThat(reopened.memberTo).isNull()
    }

    @Test
    fun `re-open is rejected when a later membership would overlap`() {
        val f = fixture()
        val newMember = newComponent(f.typeId)
        mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t2, null) // closes f.componentId at t2
        val closedId = membershipId(f.slotId, f.componentId)

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.correctMembership(closedId, null, null, reopen = true)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBERSHIP_OVERLAP)
    }

    // --- validation ---------------------------------------------------------------------------

    @Test
    fun `correct rejects an empty correction and a non-positive interval`() {
        val f = fixture()
        val id = membershipId(f.slotId, f.componentId)

        val exEmpty = assertThrows<ServiceException> { mountingAssemblyService.correctMembership(id, null, null) }
        assertThat(exEmpty.getError()).isEqualTo(ErrorCodesDomain.CORRECTION_INVALID)

        val exNegative = assertThrows<ServiceException> { mountingAssemblyService.correctMembership(id, t3, t1) }
        assertThat(exNegative.getError()).isEqualTo(ErrorCodesDomain.CORRECTION_INVALID)
    }

    // --- void -----------------------------------------------------------------------------------

    @Test
    fun `void of the active membership is blocked`() {
        val f = fixture()
        val id = membershipId(f.slotId, f.componentId)

        val ex = assertThrows<ServiceException> { mountingAssemblyService.voidMembership(id) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBERSHIP_VOID_BLOCKED)
    }

    @Test
    fun `void of a fully coincident closed membership deletes both rows`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val secondType = newType()
        newMountPoint(bikeId, secondType)
        val secondSlot = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "second", componentTypeId = secondType, validFrom = t1)
        ).id!!
        val secondComponent = newComponent(secondType)
        mountingAssemblyService.addMember(f.assemblyId, secondComponent, secondSlot, t2, mountPointId = null)
        // removeMember closes membership and its governed mounting together - fully coincident [t2, t3)
        mountingAssemblyService.removeMember(secondComponent, t3)
        val id = membershipId(secondSlot, secondComponent)

        mountingAssemblyService.voidMembership(id)

        assertThat(assemblyService.getMemberships(secondSlot, null, null)).isEmpty()
        assertThat(mountingService.getMountings(secondComponent, null, null, null)).isEmpty()
    }

    @Test
    fun `void of a non-coincident membership de-adopts the overlapping governed mounting instead of being blocked`() {
        // CE-0106 adoption: direct mount predates the membership, joined while unmounted,
        // adopted (not re-created) when the assembly is mounted later - mountedAt stays at
        // the original direct-mount time, independent of the membership's memberFrom.
        val typeId = newType()
        val componentId = newComponent(typeId)
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        mountingService.mount(bikeId, mountPointId, componentId, t0)

        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "adopt-${UUID.randomUUID()}")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        mountingAssemblyService.addMember(assemblyId, componentId, slotId, t1, mountPointId = null)
        mountingAssemblyService.mountAssembly(assemblyId, bikeId, t2, emptyList())
        assertThat(mountingService.getMountings(componentId, null, null, null).single().mountedAt).isEqualTo(t0)

        // removeMember closes both regardless of coincidence: membership [t1,t3), mounting [t0,t3)
        mountingAssemblyService.removeMember(componentId, t3)
        val id = membershipId(slotId, componentId)

        mountingAssemblyService.voidMembership(id)

        assertThat(assemblyService.getMemberships(slotId, null, null)).isEmpty()
        // adopted row survives, de-adopted - the direct-mounting fact [t0,t3) stays true
        val mounting = mountingService.getMountings(componentId, null, null, null).single()
        assertThat(mounting.assemblyMountingId).isNull()
        assertThat(mounting.mountedAt).isEqualTo(t0)
        assertThat(mounting.dismountedAt).isEqualTo(t3)
    }

    // --- wire tri-state (MockMvc) ---------------------------------------------------------------

    @Test
    fun `wire tri-state - omitted keeps, explicit null re-opens, null memberFrom is 400`() {
        val f = fixture()
        mountingAssemblyService.removeMember(f.componentId, t2)
        val id = membershipId(f.slotId, f.componentId)
        val path = "/memberships/$id/action/correct"

        // omitted memberTo keeps the current value
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberFrom":"2024-01-15T00:00:00Z"}"""))
            .andExpect(status().isOk)
        assertThat(assemblyService.getMemberships(f.slotId, null, null).single().memberTo).isEqualTo(t2)

        // explicit null memberTo re-opens
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberTo":null}"""))
            .andExpect(status().isOk)
        assertThat(assemblyService.getMemberships(f.slotId, null, null).single().memberTo).isNull()

        // explicit null memberFrom is invalid
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"memberFrom":null}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("CORRECTION_INVALID"))
    }
}
