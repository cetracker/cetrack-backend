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
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class AssemblyMountingCorrectionIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var assemblyService: AssemblyService
    @Autowired private lateinit var mountingAssemblyService: AssemblyMountingService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val t0: Instant = Instant.parse("2023-12-01T00:00:00Z")
    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val t3: Instant = Instant.parse("2024-03-01T00:00:00Z")
    private val t4: Instant = Instant.parse("2024-04-01T00:00:00Z")

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    private fun newBike(): UUID = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!

    private fun newMountPoint(bikeId: UUID, typeId: UUID, name: String = "mp-${UUID.randomUUID()}"): UUID =
        compositionService.addMountPoint(DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = name)).id!!

    private fun newComponent(typeId: UUID, label: String = "comp-${UUID.randomUUID()}"): UUID =
        componentService.addComponent(DomainComponent(componentTypeId = typeId, label = label)).id!!

    private fun isAdopted(mountingId: UUID): Boolean =
        jdbc.queryForObject("SELECT adopted FROM mounting WHERE id = ?", Boolean::class.java, mountingId)!!

    /**
     * One assembly, two slots, each accepting its own component type - lets a test give one
     * member a "created" governed mounting and the other an "adopted" one (CE-0106) side by side.
     */
    private data class TwoSlotFixture(
        val assemblyId: UUID, val bikeId: UUID,
        val slotA: UUID, val typeA: UUID, val componentA: UUID,
        val slotB: UUID, val typeB: UUID, val componentB: UUID, val mountPointB: UUID,
    )

    /**
     * @param adoptComponentBAt when set, componentB is directly mounted at this instant and
     * then joins slotB while the assembly is still unmounted (CE-0106 order: mount, then
     * addMember) - so a later mountAssembly adopts its mounting instead of creating one. When
     * null, componentB's membership is inserted directly with no prior mounting - mountAssembly
     * then creates a fresh governed mounting for it, like componentA always gets.
     */
    private fun twoSlotFixture(adoptComponentBAt: Instant? = null): TwoSlotFixture {
        val typeA = newType()
        val typeB = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "asm-${UUID.randomUUID()}")).id!!
        val slotA = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "a", componentTypeId = typeA, validFrom = t1)
        ).id!!
        val slotB = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "b", componentTypeId = typeB, validFrom = t1)
        ).id!!
        val componentA = newComponent(typeA)
        val componentB = newComponent(typeB)
        val bikeId = newBike()
        newMountPoint(bikeId, typeA)
        val mountPointB = newMountPoint(bikeId, typeB)
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            componentA, slotA, java.sql.Timestamp.from(t1)
        )
        if (adoptComponentBAt != null) {
            mountingService.mount(bikeId, mountPointB, componentB, adoptComponentBAt)
            mountingAssemblyService.addMember(assemblyId, componentB, slotB, adoptComponentBAt, mountPointId = null)
        } else {
            jdbc.update(
                "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
                componentB, slotB, java.sql.Timestamp.from(t1)
            )
        }
        return TwoSlotFixture(assemblyId, bikeId, slotA, typeA, componentA, slotB, typeB, componentB, mountPointB)
    }

    // --- correct: mountedAt cascade, provenance-gated ------------------------------------------

    @Test
    fun `correct mountedAt cascades to a created governed mounting but leaves an adopted one untouched, even a degenerate one starting at the same instant`() {
        // componentB directly mounted at the exact instant the assembly will be mounted -
        // mountAssembly adopts it in place (mountedAt stays f's mount instant, adopted = true)
        val f = twoSlotFixture(adoptComponentBAt = t2)
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t2, emptyList())
        val mountingId = result.assemblyMounting.id!!

        val createdMounting = mountingService.getMountings(f.componentA, null, null, null).single()
        val adoptedMounting = mountingService.getMountings(f.componentB, null, null, null).single()
        assertThat(isAdopted(createdMounting.id)).isFalse()
        assertThat(isAdopted(adoptedMounting.id)).isTrue()

        mountingAssemblyService.correctAssemblyMounting(f.assemblyId, mountingId, t0, null)

        assertThat(mountingService.getMountings(f.componentA, null, null, null).single().mountedAt).isEqualTo(t0)
        // adopted row's start predates governance by construction - left untouched by the mountedAt cascade
        assertThat(mountingService.getMountings(f.componentB, null, null, null).single().mountedAt).isEqualTo(t2)
    }

    @Test
    fun `correct dismountedAt cascades to created and adopted governed mountings alike when it coincides`() {
        val f = twoSlotFixture(adoptComponentBAt = t2)
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t2, emptyList())
        val mountingId = result.assemblyMounting.id!!
        mountingAssemblyService.dismountAssembly(f.assemblyId, t3)

        mountingAssemblyService.correctAssemblyMounting(f.assemblyId, mountingId, null, t4)

        assertThat(mountingService.getMountings(f.componentA, null, null, null).single().dismountedAt).isEqualTo(t4)
        // dismountAssembly imposed this boundary on the adopted row too - it tracks the assembly
        assertThat(mountingService.getMountings(f.componentB, null, null, null).single().dismountedAt).isEqualTo(t4)
    }

    // --- correct: re-open + overlaps ------------------------------------------------------------

    @Test
    fun `re-open is rejected when a later assembly mounting of the same assembly would overlap`() {
        val f = twoSlotFixture()
        val first = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t1, emptyList())
        mountingAssemblyService.dismountAssembly(f.assemblyId, t2)
        mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t3, emptyList())

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.correctAssemblyMounting(f.assemblyId, first.assemblyMounting.id!!, null, null, reopen = true)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_MOUNTING_OVERLAP)
    }

    @Test
    fun `correct rejects a cascaded mounting overlap with an unrelated historical mounting of the same component`() {
        val f = twoSlotFixture()
        // componentA's own history on its eventual mount point, before it ever became a
        // governed member - overlap bait, [t0,t1)
        val mountPointA = compositionService.getMountPoints(f.bikeId).first { it.componentTypeId == f.typeA }.id!!
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?)",
            f.componentA, mountPointA, java.sql.Timestamp.from(t0), java.sql.Timestamp.from(t1)
        )

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t2, emptyList())
        val mountingId = result.assemblyMounting.id!!
        mountingAssemblyService.dismountAssembly(f.assemblyId, t3)

        // moving mountedAt back to t0 overlaps componentA's closed historical mounting [t0,t1)
        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.correctAssemblyMounting(f.assemblyId, mountingId, t0, null)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
    }

    // --- correct: validation --------------------------------------------------------------------

    @Test
    fun `correct rejects an empty correction and a non-positive interval`() {
        val f = twoSlotFixture()
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t1, emptyList())
        val mountingId = result.assemblyMounting.id!!

        val exEmpty = assertThrows<ServiceException> {
            mountingAssemblyService.correctAssemblyMounting(f.assemblyId, mountingId, null, null)
        }
        assertThat(exEmpty.getError()).isEqualTo(ErrorCodesDomain.CORRECTION_INVALID)

        val exNegative = assertThrows<ServiceException> {
            mountingAssemblyService.correctAssemblyMounting(f.assemblyId, mountingId, t3, t1)
        }
        assertThat(exNegative.getError()).isEqualTo(ErrorCodesDomain.CORRECTION_INVALID)
    }

    @Test
    fun `correct with a mismatched assembly id is 404`() {
        val f = twoSlotFixture()
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t1, emptyList())
        val otherAssemblyId = assemblyService.createAssembly(DomainAssembly(name = "other-${UUID.randomUUID()}")).id!!

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.correctAssemblyMounting(otherAssemblyId, result.assemblyMounting.id!!, t0, null)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_MOUNTING_NOT_FOUND)
    }

    // --- void -------------------------------------------------------------------------------------

    @Test
    fun `void of the active assembly mounting deletes created governed mountings and de-adopts adopted ones`() {
        val f = twoSlotFixture(adoptComponentBAt = t2)
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t2, emptyList())
        val mountingId = result.assemblyMounting.id!!

        mountingAssemblyService.voidAssemblyMounting(f.assemblyId, mountingId)

        assertThat(assemblyService.getAssemblyMountings(f.assemblyId)).isEmpty()
        assertThat(mountingService.getMountings(f.componentA, null, null, null)).isEmpty() // created row deleted
        val deAdopted = mountingService.getMountings(f.componentB, null, null, null).single() // adopted row survives
        assertThat(deAdopted.assemblyMountingId).isNull()
        assertThat(deAdopted.dismountedAt).isNull()
        assertThat(deAdopted.mountedAt).isEqualTo(t2)
        assertThat(isAdopted(deAdopted.id)).isFalse()
    }

    @Test
    fun `void of a closed assembly mounting de-adopts without erasing the dismountedAt it imposed`() {
        val f = twoSlotFixture(adoptComponentBAt = t0)
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t2, emptyList())
        mountingAssemblyService.dismountAssembly(f.assemblyId, t3)

        mountingAssemblyService.voidAssemblyMounting(f.assemblyId, result.assemblyMounting.id!!)

        val deAdopted = mountingService.getMountings(f.componentB, null, null, null).single()
        assertThat(deAdopted.assemblyMountingId).isNull()
        // the mounting fact stays true: componentB really was on the bike [t0,t3) - only governance is removed
        assertThat(deAdopted.mountedAt).isEqualTo(t0)
        assertThat(deAdopted.dismountedAt).isEqualTo(t3)
    }

    @Test
    fun `void with a mismatched assembly id is 404`() {
        val f = twoSlotFixture()
        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t1, emptyList())
        val otherAssemblyId = assemblyService.createAssembly(DomainAssembly(name = "other-${UUID.randomUUID()}")).id!!

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.voidAssemblyMounting(otherAssemblyId, result.assemblyMounting.id!!)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_MOUNTING_NOT_FOUND)
    }

    // --- adopted flag bookkeeping ----------------------------------------------------------------

    @Test
    fun `adopted is set on both adoption paths and left false on created rows and on occupants mountAssembly closes`() {
        val f = twoSlotFixture()
        // an unrelated occupant sits at componentA's resolved mount point before the assembly ever mounts
        val occupantType = f.typeA
        val occupant = newComponent(occupantType)
        val mountPointA = compositionService.getMountPoints(f.bikeId).first { it.componentTypeId == f.typeA }.id!!
        mountingService.mount(f.bikeId, mountPointA, occupant, t0)

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, f.bikeId, t2, emptyList())
        val mountingId = result.assemblyMounting.id!!

        val createdMounting = mountingService.getMountings(f.componentA, null, null, null).single()
        assertThat(isAdopted(createdMounting.id)).isFalse()
        val occupantMounting = mountingService.getMountings(occupant, null, null, null).single()
        assertThat(occupantMounting.dismountedAt).isEqualTo(t2) // closed by mountAssembly's eviction
        assertThat(occupantMounting.assemblyMountingId).isNull() // never governed - just displaced
        assertThat(isAdopted(occupantMounting.id)).isFalse()

        // componentB adopts via addMember while the assembly is already mounted (mountMemberIntoGovernedSlot)
        val typeC = newType()
        val slotC = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "c", componentTypeId = typeC, validFrom = t1)
        ).id!!
        val componentC = newComponent(typeC)
        val mountPointC = newMountPoint(f.bikeId, typeC)
        mountingService.mount(f.bikeId, mountPointC, componentC, t3)

        mountingAssemblyService.addMember(f.assemblyId, componentC, slotC, t4, mountPointId = null)

        val adoptedViaAddMember = mountingService.getMountings(componentC, null, null, null).single()
        assertThat(adoptedViaAddMember.assemblyMountingId).isEqualTo(mountingId)
        assertThat(adoptedViaAddMember.mountedAt).isEqualTo(t3) // unchanged - adopted, not re-created
        assertThat(isAdopted(adoptedViaAddMember.id)).isTrue()
    }
}
