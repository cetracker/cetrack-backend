package de.cyclingsir.cetrack.assembly

import de.cyclingsir.cetrack.assembly.domain.AssemblyMountingService
import de.cyclingsir.cetrack.assembly.domain.AssemblyService
import de.cyclingsir.cetrack.assembly.domain.DomainAssembly
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblySlot
import de.cyclingsir.cetrack.assembly.domain.DomainPlannedSlotState
import de.cyclingsir.cetrack.assembly.domain.DomainSlotResolution
import de.cyclingsir.cetrack.bike.domain.BikeCompositionService
import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.domain.DomainMountPoint
import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.catalog.domain.DomainPosition
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipAction
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipChange
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class AssemblyMountingIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var assemblyService: AssemblyService
    @Autowired private lateinit var mountingAssemblyService: AssemblyMountingService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val t3: Instant = Instant.parse("2024-03-01T00:00:00Z")

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    private fun newBike(): UUID = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!

    private fun newMountPoint(bikeId: UUID, typeId: UUID, positionId: UUID? = null, name: String = "mp-${UUID.randomUUID()}"): UUID =
        compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, positionId = positionId, name = name)
        ).id!!

    private fun newComponent(typeId: UUID, label: String = "comp-${UUID.randomUUID()}"): UUID =
        componentService.addComponent(DomainComponent(componentTypeId = typeId, label = label)).id!!

    private fun newPosition(): UUID = catalogService.addPosition(DomainPosition(name = "pos-${UUID.randomUUID()}")).id!!

    /** One assembly, one slot, one member component - the common fixture. */
    private data class Fixture(val assemblyId: UUID, val slotId: UUID, val typeId: UUID, val componentId: UUID)

    private fun fixture(positionId: UUID? = null, typeId: UUID = newType()): Fixture {
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "asm-${UUID.randomUUID()}", positionId = positionId)).id!!
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

    /** N-slot assembly of one component type, no members - callers add membership + pin slot mappings. */
    private fun multiSlotAssembly(name: String, typeId: UUID, slotCount: Int): Pair<UUID, List<UUID>> {
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = name)).id!!
        val slots = (1..slotCount).map { i ->
            assemblyService.createAssemblySlot(
                assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "s$i", componentTypeId = typeId, validFrom = t1)
            ).id!!
        }
        return assemblyId to slots
    }

    private fun addMember(slotId: UUID, componentId: UUID, from: Instant = t1) {
        jdbc.update("INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            componentId, slotId, java.sql.Timestamp.from(from))
    }

    private fun pinSlotMapping(slotId: UUID, bikeId: UUID, mountPointId: UUID) {
        jdbc.update("INSERT INTO slot_mapping (assembly_slot_id, bike_id, mount_point_id) VALUES (?, ?, ?)",
            slotId, bikeId, mountPointId)
    }

    // --- ADR-0003 resolution outcomes -----------------------------------------------------

    @Test
    fun `resolution outcome 1 - unique candidate needs no dialog`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.created).hasSize(1)
        assertThat(result.changes.created.single().mountPointId).isEqualTo(mountPointId)
        assertThat(result.changes.created.single().componentId).isEqualTo(f.componentId)
    }

    @Test
    fun `resolution outcome 2 - position filter narrows multiple candidates`() {
        val front = newPosition()
        val rear = newPosition()
        val f = fixture(positionId = rear)
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId, positionId = front)
        val rearMp = newMountPoint(bikeId, f.typeId, positionId = rear)

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.created.single().mountPointId).isEqualTo(rearMp)
    }

    @Test
    fun `resolution outcome 3 - stored slot mapping resolves an ambiguous slot`() {
        val f = fixture()
        val bikeId = newBike()
        val mpA = newMountPoint(bikeId, f.typeId)
        val mpB = newMountPoint(bikeId, f.typeId)
        jdbc.update(
            "INSERT INTO slot_mapping (assembly_slot_id, bike_id, mount_point_id) VALUES (?, ?, ?)",
            f.slotId, bikeId, mpB
        )

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.created.single().mountPointId).isEqualTo(mpB)
        assertThat(result.rememberedSlotMappings).isEmpty() // already stored - nothing new to remember
        assertThat(mpA).isNotEqualTo(mpB)
    }

    @Test
    fun `resolution outcome 4 - ask-once persists the answer, second mount needs no input`() {
        val f = fixture()
        val bikeId = newBike()
        val mpA = newMountPoint(bikeId, f.typeId)
        val mpB = newMountPoint(bikeId, f.typeId)

        val plan = mountingAssemblyService.planMount(f.assemblyId, bikeId, t2)
        assertThat(plan.slots.single().state).isEqualTo(DomainPlannedSlotState.UNRESOLVED)
        assertThat(plan.slots.single().candidates.map { it.mountPointId }).containsExactlyInAnyOrder(mpA, mpB)

        val result = mountingAssemblyService.mountAssembly(
            f.assemblyId, bikeId, t2, listOf(DomainSlotResolution(f.slotId, mpB))
        )
        assertThat(result.changes.created.single().mountPointId).isEqualTo(mpB)
        assertThat(result.rememberedSlotMappings).singleElement().extracting { it.mountPointId }.isEqualTo(mpB)

        // dismount and re-mount: the plan is now resolved without any input
        mountingAssemblyService.dismountAssembly(f.assemblyId, Instant.parse("2024-03-01T00:00:00Z"))
        val secondPlan = mountingAssemblyService.planMount(f.assemblyId, bikeId, Instant.parse("2024-04-01T00:00:00Z"))
        assertThat(secondPlan.slots.single().state).isEqualTo(DomainPlannedSlotState.RESOLVED)
        assertThat(secondPlan.slots.single().mountPointId).isEqualTo(mpB)
    }

    @Test
    fun `resolution outcome 5 - member already at the resolved mount point is adopted, elsewhere is rejected`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        // a member of a not-yet-mounted assembly directly mounted is a guided choice at the
        // mount() API (ADR-0001 §3) - seed the historical fact via SQL, as MountDismountIT does.
        jdbc.update("INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
            f.componentId, mountPointId, java.sql.Timestamp.from(t1))

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.created).isEmpty() // adopted, not re-created
        assertThat(result.changes.closed).isEmpty()
        val adopted = mountingService.getMountings(f.componentId, null, null, null).single()
        assertThat(adopted.assemblyMountingId).isEqualTo(result.assemblyMounting.id)

        // elsewhere: a second member mounted (directly, on a different bike) is rejected
        val f2 = fixture()
        newMountPoint(bikeId, f2.typeId) // the resolvable candidate on the mounting bike
        val elsewhereBike = newBike()
        val elsewhereMountPoint = newMountPoint(elsewhereBike, f2.typeId)
        jdbc.update("INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
            f2.componentId, elsewhereMountPoint, java.sql.Timestamp.from(t1))

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(f2.assemblyId, bikeId, t2, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE)
    }

    // --- Atomicity -------------------------------------------------------------------------

    @Test
    fun `one member conflict rolls back all member mountings`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "multi")).id!!
        val slotOk = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "ok", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val slotConflict = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "conflict", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val okComponent = newComponent(typeId)
        val conflictComponent = newComponent(typeId)
        jdbc.update("INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            okComponent, slotOk, java.sql.Timestamp.from(t1))
        jdbc.update("INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            conflictComponent, slotConflict, java.sql.Timestamp.from(t1))

        val bikeId = newBike()
        newMountPoint(bikeId, typeId) // slotOk resolves here
        val elsewhereBike = newBike()
        val elsewhereMp = newMountPoint(elsewhereBike, typeId)
        // seed the historical fact via SQL (ADR-0001 §3 would guided-choice-block mount() here)
        jdbc.update("INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
            conflictComponent, elsewhereMp, java.sql.Timestamp.from(t1))

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(assemblyId, bikeId, t2, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE)

        assertThat(assemblyService.getAssemblyMountings(assemblyId)).isEmpty()
        assertThat(mountingService.getMountings(okComponent, null, null, null)).isEmpty()
        assertThat(mountingService.getMountings(conflictComponent, null, null, null)).hasSize(1) // untouched, still on elsewhereBike
    }

    // --- Occupant policy ---------------------------------------------------------------------

    @Test
    fun `direct occupant is evicted on mount`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        val directOccupant = newComponent(f.typeId)
        mountingService.mount(bikeId, mountPointId, directOccupant, t1)

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.closed.single().componentId).isEqualTo(directOccupant)
        assertThat(result.changes.created.single().componentId).isEqualTo(f.componentId)
    }

    // --- CE-0120: occupancy resolved at mount time T, not now ---------------------------------

    @Test
    fun `direct occupant closed after T is still evicted and shortened at T - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        val directOccupant = newComponent(f.typeId)
        mountingService.mount(bikeId, mountPointId, directOccupant, t1)
        mountingService.dismount(directOccupant, t3)

        val plan = mountingAssemblyService.planMount(f.assemblyId, bikeId, t2)
        assertThat(plan.slots.single().state).isEqualTo(DomainPlannedSlotState.RESOLVED)
        assertThat(plan.slots.single().willDismountComponentId).isEqualTo(directOccupant)

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.closed.single().componentId).isEqualTo(directOccupant)
        assertThat(result.changes.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(result.changes.created.single().componentId).isEqualTo(f.componentId)
        assertThat(mountingService.getMountings(directOccupant, null, null, null).single().dismountedAt).isEqualTo(t2)
    }

    @Test
    fun `direct occupant opened after T is not the occupant - collides instead of backdated - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        val directOccupant = newComponent(f.typeId)
        mountingService.mount(bikeId, mountPointId, directOccupant, t2)

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
        assertThat(mountingService.getMountings(directOccupant, null, null, null).single().dismountedAt).isNull()
    }

    @Test
    fun `direct occupant mounted exactly at T is backdated, nothing mutated - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        val directOccupant = newComponent(f.typeId)
        mountingService.mount(bikeId, mountPointId, directOccupant, t1)

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_BACKDATED)
        assertThat(mountingService.getMountings(directOccupant, null, null, null).single().dismountedAt).isNull()
    }

    @Test
    fun `direct occupant closed exactly at T is adjacent, not evicted - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        val directOccupant = newComponent(f.typeId)
        mountingService.mount(bikeId, mountPointId, directOccupant, t1)
        mountingService.dismount(directOccupant, t2)

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.closed).isEmpty()
        assertThat(result.changes.created.single().componentId).isEqualTo(f.componentId)
    }

    @Test
    fun `member's own closed mounting containing T at the resolved point is shortened, not adopted - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        // f.componentId is already a member of an unmounted assembly - mount() would guided-choice-block it
        // (ADR-0001 §3); seed the historical closed fact via SQL instead, as resolution outcome 5 does.
        jdbc.update("INSERT INTO mounting (component_id, mount_point_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?)",
            f.componentId, mountPointId, java.sql.Timestamp.from(t1), java.sql.Timestamp.from(t3))

        val result = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList())
        assertThat(result.changes.created.single().componentId).isEqualTo(f.componentId)
        assertThat(result.changes.closed.single().componentId).isEqualTo(f.componentId)
        assertThat(result.changes.closed.single().dismountedAt).isEqualTo(t2)

        val rows = mountingService.getMountings(f.componentId, null, null, null)
        assertThat(rows).hasSize(2)
        val ownRow = rows.single { it.dismountedAt == t2 }
        assertThat(ownRow.assemblyMountingId).isNull() // the pre-existing own row, untouched by adoption
        val governedRow = rows.single { it.dismountedAt == null }
        assertThat(governedRow.assemblyMountingId).isEqualTo(result.assemblyMounting.id)
    }

    @Test
    fun `mount colliding with an already-closed governed occupant 409s without touching it - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())
        mountingAssemblyService.dismountAssembly(f.assemblyId, t3)

        val g = fixture(typeId = f.typeId)
        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.mountAssembly(g.assemblyId, bikeId, t2, emptyList())
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)

        // rollback-safe AND non-destructive by design: neither the assembly mounting nor its
        // governed row was ever mutated, since the override path skips already-closed blockers.
        assertThat(assemblyService.getAssemblyMountings(f.assemblyId).single().dismountedAt).isEqualTo(t3)
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().dismountedAt).isEqualTo(t3)
    }

    @Test
    fun `addMember at T evicts a closed direct occupant and collides with one opened after T - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val secondTypeId = newType()
        val secondSlotId = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "second", componentTypeId = secondTypeId, validFrom = t1)
        ).id!!
        val secondMountPoint = newMountPoint(bikeId, secondTypeId)
        val closedOccupant = newComponent(secondTypeId)
        mountingService.mount(bikeId, secondMountPoint, closedOccupant, t1)
        mountingService.dismount(closedOccupant, t3)
        val secondComponent = newComponent(secondTypeId)

        val addChanges = mountingAssemblyService.addMember(f.assemblyId, secondComponent, secondSlotId, t2, mountPointId = null)
        assertThat(addChanges.closed.single().componentId).isEqualTo(closedOccupant)
        assertThat(addChanges.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(addChanges.created.single().componentId).isEqualTo(secondComponent)

        // a fresh slot resolving to a point occupied by something that only started after `from`
        // is not "the occupant" at `from` - the governed insert collides on GiST instead
        val thirdTypeId = newType()
        val thirdSlotId = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "third", componentTypeId = thirdTypeId, validFrom = t1)
        ).id!!
        val thirdMountPoint = newMountPoint(bikeId, thirdTypeId)
        val openedAfterFrom = newComponent(thirdTypeId)
        mountingService.mount(bikeId, thirdMountPoint, openedAfterFrom, t2)
        val thirdComponent = newComponent(thirdTypeId)
        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(f.assemblyId, thirdComponent, thirdSlotId, t1, mountPointId = null)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
        assertThat(mountPointId).isNotNull()
    }

    @Test
    fun `addMember does not adopt the new member's own closed mounting at the resolved point - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val secondTypeId = newType()
        val secondSlotId = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "second", componentTypeId = secondTypeId, validFrom = t1)
        ).id!!
        val secondMountPoint = newMountPoint(bikeId, secondTypeId)
        val secondComponent = newComponent(secondTypeId)
        mountingService.mount(bikeId, secondMountPoint, secondComponent, t1)
        mountingService.dismount(secondComponent, t3)

        val addChanges = mountingAssemblyService.addMember(f.assemblyId, secondComponent, secondSlotId, t2, mountPointId = null)
        assertThat(addChanges.closed.single().componentId).isEqualTo(secondComponent)
        assertThat(addChanges.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(addChanges.created.single().componentId).isEqualTo(secondComponent)

        val rows = mountingService.getMountings(secondComponent, null, null, null)
        assertThat(rows).hasSize(2)
        val ownRow = rows.single { it.dismountedAt == t2 }
        assertThat(ownRow.assemblyMountingId).isNull()
        val governedRow = rows.single { it.dismountedAt == null }
        assertThat(governedRow.mountPointId).isEqualTo(secondMountPoint)
    }

    // --- CE-0119: mounting overrides a governed occupant instead of rejecting -----------------

    @Test
    fun `mounting overrides another assembly's governed occupant - CE-0119`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId) // the only candidate - both assemblies below target it
        val blocked = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val g = fixture(typeId = f.typeId)
        val result = mountingAssemblyService.mountAssembly(g.assemblyId, bikeId, t2, emptyList())

        assertThat(result.changes.created.single().componentId).isEqualTo(g.componentId)
        assertThat(result.changes.closed.single().componentId).isEqualTo(f.componentId)
        assertThat(result.dismountedAssemblyMountings).singleElement()
            .extracting { it.id }.isEqualTo(blocked.assemblyMounting.id)

        assertThat(assemblyService.getAssemblyMountings(f.assemblyId).single().dismountedAt).isEqualTo(t2)
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().dismountedAt).isEqualTo(t2)
        assertThat(mountingService.getMountings(g.componentId, null, null, null).single().dismountedAt).isNull()
    }

    @Test
    fun `mounting overrides a blocker colliding on every slot - CE-0119 regression`() {
        val typeId = newType()
        val bikeId = newBike()
        val mp1 = newMountPoint(bikeId, typeId)
        val mp2 = newMountPoint(bikeId, typeId)

        val (assemblyA, slotsA) = multiSlotAssembly("A", typeId, 2)
        val componentsA = slotsA.map { newComponent(typeId) }
        slotsA.zip(componentsA).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsA[0], bikeId, mp1)
        pinSlotMapping(slotsA[1], bikeId, mp2)
        mountingAssemblyService.mountAssembly(assemblyA, bikeId, t1, emptyList())

        val (assemblyB, slotsB) = multiSlotAssembly("B", typeId, 2)
        val componentsB = slotsB.map { newComponent(typeId) }
        slotsB.zip(componentsB).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsB[0], bikeId, mp1)
        pinSlotMapping(slotsB[1], bikeId, mp2)

        val result = mountingAssemblyService.mountAssembly(assemblyB, bikeId, t2, emptyList())

        assertThat(result.dismountedAssemblyMountings).singleElement().extracting { it.assemblyId }.isEqualTo(assemblyA)
        assertThat(result.changes.closed).hasSize(2)
        assertThat(result.changes.created).hasSize(2)
        assertThat(assemblyService.getAssemblyMountings(assemblyA).single().dismountedAt).isEqualTo(t2)
        componentsA.forEach { c -> assertThat(mountingService.getMountings(c, null, null, null).single().dismountedAt).isEqualTo(t2) }
        componentsB.forEach { c -> assertThat(mountingService.getMountings(c, null, null, null).single().dismountedAt).isNull() }
    }

    @Test
    fun `mounting overrides a blocker fully on partial overlap, including its non-colliding slot - CE-0119`() {
        val typeId = newType()
        val bikeId = newBike()
        val mp1 = newMountPoint(bikeId, typeId)
        val mp2 = newMountPoint(bikeId, typeId)
        val mp3 = newMountPoint(bikeId, typeId)

        val (assemblyA, slotsA) = multiSlotAssembly("A", typeId, 2)
        val componentsA = slotsA.map { newComponent(typeId) }
        slotsA.zip(componentsA).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsA[0], bikeId, mp1)
        pinSlotMapping(slotsA[1], bikeId, mp2)
        mountingAssemblyService.mountAssembly(assemblyA, bikeId, t1, emptyList())

        val (assemblyB, slotsB) = multiSlotAssembly("B", typeId, 2)
        val componentsB = slotsB.map { newComponent(typeId) }
        slotsB.zip(componentsB).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsB[0], bikeId, mp2) // collides with A
        pinSlotMapping(slotsB[1], bikeId, mp3) // does not collide with A

        mountingAssemblyService.mountAssembly(assemblyB, bikeId, t2, emptyList())

        // A is fully dismounted, including the mp1 member B never targeted
        componentsA.forEach { c -> assertThat(mountingService.getMountings(c, null, null, null).single().dismountedAt).isEqualTo(t2) }
        assertThat(assemblyService.getAssemblyMountings(assemblyA).single().dismountedAt).isEqualTo(t2)
    }

    @Test
    fun `mounting overrides two distinct blocking assemblies - CE-0119`() {
        val typeId = newType()
        val bikeId = newBike()
        val mp1 = newMountPoint(bikeId, typeId)
        val mp2 = newMountPoint(bikeId, typeId)

        val (assemblyA, slotsA) = multiSlotAssembly("A", typeId, 1)
        val componentA = newComponent(typeId)
        addMember(slotsA[0], componentA)
        pinSlotMapping(slotsA[0], bikeId, mp1)
        mountingAssemblyService.mountAssembly(assemblyA, bikeId, t1, emptyList())

        val (assemblyC, slotsC) = multiSlotAssembly("C", typeId, 1)
        val componentC = newComponent(typeId)
        addMember(slotsC[0], componentC)
        pinSlotMapping(slotsC[0], bikeId, mp2)
        mountingAssemblyService.mountAssembly(assemblyC, bikeId, t1, emptyList())

        val (assemblyB, slotsB) = multiSlotAssembly("B", typeId, 2)
        val componentsB = slotsB.map { newComponent(typeId) }
        slotsB.zip(componentsB).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsB[0], bikeId, mp1)
        pinSlotMapping(slotsB[1], bikeId, mp2)

        val result = mountingAssemblyService.mountAssembly(assemblyB, bikeId, t2, emptyList())

        assertThat(result.dismountedAssemblyMountings.map { it.assemblyId }).containsExactlyInAnyOrder(assemblyA, assemblyC)
        assertThat(mountingService.getMountings(componentA, null, null, null).single().dismountedAt).isEqualTo(t2)
        assertThat(mountingService.getMountings(componentC, null, null, null).single().dismountedAt).isEqualTo(t2)
    }

    @Test
    fun `mounting override backdated relative to the blocker collides instead - CE-0120`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList()) // A mounted at t2

        val g = fixture(typeId = f.typeId)
        // t1 predates A's start - A isn't active at t1, so it isn't "the occupant" to evict;
        // the insert collides with A's still-open governed row on GiST instead (CE-0120).
        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.mountAssembly(g.assemblyId, bikeId, t1, emptyList())
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)

        assertThat(assemblyService.getAssemblyMountings(f.assemblyId).single().dismountedAt).isNull()
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().dismountedAt).isNull()
    }

    @Test
    fun `mount atomicity - a fellow slot failing elsewhere leaves an already-planned override untouched - CE-0119`() {
        val typeId = newType()
        val bikeId = newBike()
        val mp1 = newMountPoint(bikeId, typeId)
        val mp2 = newMountPoint(bikeId, typeId)

        val (assemblyA, slotsA) = multiSlotAssembly("A", typeId, 1)
        val componentA = newComponent(typeId)
        addMember(slotsA[0], componentA)
        pinSlotMapping(slotsA[0], bikeId, mp1)
        mountingAssemblyService.mountAssembly(assemblyA, bikeId, t1, emptyList())

        val (assemblyB, slotsB) = multiSlotAssembly("B", typeId, 2)
        val overridingComponent = newComponent(typeId)
        val elsewhereComponent = newComponent(typeId)
        addMember(slotsB[0], overridingComponent)
        addMember(slotsB[1], elsewhereComponent)
        pinSlotMapping(slotsB[0], bikeId, mp1) // would override A
        pinSlotMapping(slotsB[1], bikeId, mp2)
        val elsewhereBike = newBike()
        val elsewhereMp = newMountPoint(elsewhereBike, typeId)
        // seed the historical fact via SQL (ADR-0001 §3 would guided-choice-block mount() here, elsewhereComponent is a member)
        jdbc.update("INSERT INTO mounting (component_id, mount_point_id, mounted_at) VALUES (?, ?, ?)",
            elsewhereComponent, elsewhereMp, java.sql.Timestamp.from(t1)) // unrelated failure on the other slot

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(assemblyB, bikeId, t2, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE)

        assertThat(assemblyService.getAssemblyMountings(assemblyA).single().dismountedAt).isNull()
        assertThat(mountingService.getMountings(componentA, null, null, null).single().dismountedAt).isNull()
    }

    @Test
    fun `planMount resolves a governed occupant and lists it in assembliesToDismount, deduped - CE-0119`() {
        val typeId = newType()
        val bikeId = newBike()
        val mp1 = newMountPoint(bikeId, typeId)
        val mp2 = newMountPoint(bikeId, typeId)

        val (assemblyA, slotsA) = multiSlotAssembly("A", typeId, 2)
        val componentsA = slotsA.map { newComponent(typeId) }
        slotsA.zip(componentsA).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsA[0], bikeId, mp1)
        pinSlotMapping(slotsA[1], bikeId, mp2)
        val blocked = mountingAssemblyService.mountAssembly(assemblyA, bikeId, t1, emptyList())

        val (assemblyB, slotsB) = multiSlotAssembly("B", typeId, 2)
        val componentsB = slotsB.map { newComponent(typeId) }
        slotsB.zip(componentsB).forEach { (s, c) -> addMember(s, c) }
        pinSlotMapping(slotsB[0], bikeId, mp1)
        pinSlotMapping(slotsB[1], bikeId, mp2)

        val plan = mountingAssemblyService.planMount(assemblyB, bikeId, t2)

        assertThat(plan.mountable).isTrue()
        assertThat(plan.slots).allSatisfy { assertThat(it.state).isEqualTo(DomainPlannedSlotState.RESOLVED) }
        assertThat(plan.assembliesToDismount).singleElement().satisfies({
            assertThat(it.assemblyId).isEqualTo(assemblyA)
            assertThat(it.assemblyMountingId).isEqualTo(blocked.assemblyMounting.id)
        })
        // planning changes nothing
        componentsA.forEach { c -> assertThat(mountingService.getMountings(c, null, null, null).single().dismountedAt).isNull() }
    }

    @Test
    fun `addMember into another mounted assembly's governed point still rejects - CE-0119`() {
        val typeId = newType()
        val bikeId = newBike()
        val mp1 = newMountPoint(bikeId, typeId)
        val mp2 = newMountPoint(bikeId, typeId)

        val (assemblyA, slotsA) = multiSlotAssembly("A", typeId, 1)
        val componentA = newComponent(typeId)
        addMember(slotsA[0], componentA)
        pinSlotMapping(slotsA[0], bikeId, mp1)
        mountingAssemblyService.mountAssembly(assemblyA, bikeId, t1, emptyList())

        val (assemblyB, slotsB) = multiSlotAssembly("B", typeId, 1)
        val componentB = newComponent(typeId)
        addMember(slotsB[0], componentB)
        pinSlotMapping(slotsB[0], bikeId, mp2)
        mountingAssemblyService.mountAssembly(assemblyB, bikeId, t1, emptyList())

        val extraSlot = assemblyService.createAssemblySlot(
            assemblyB, DomainAssemblySlot(assemblyId = assemblyB, name = "extra", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val newMember = newComponent(typeId)

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(assemblyB, newMember, extraSlot, t2, mp1)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_GOVERNED)
        assertThat(mountingService.getMountings(componentA, null, null, null).single().dismountedAt).isNull()
    }

    // --- Slot collision / unmountable ---------------------------------------------------------

    @Test
    fun `two slots resolving to the same mount point collide`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "collide")).id!!
        val slot1 = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "s1", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val slot2 = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "s2", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val c1 = newComponent(typeId)
        val c2 = newComponent(typeId)
        jdbc.update("INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            c1, slot1, java.sql.Timestamp.from(t1))
        jdbc.update("INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            c2, slot2, java.sql.Timestamp.from(t1))

        val bikeId = newBike()
        val onlyMountPoint = newMountPoint(bikeId, typeId)

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(assemblyId, bikeId, t2, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.SLOT_TARGET_COLLISION)
        assertThat(onlyMountPoint).isNotNull()
    }

    @Test
    fun `no candidate on the bike is unmountable`() {
        val f = fixture()
        val bikeId = newBike() // no mount points at all

        val plan = mountingAssemblyService.planMount(f.assemblyId, bikeId, t2)
        assertThat(plan.slots.single().state).isEqualTo(DomainPlannedSlotState.IMPOSSIBLE)

        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t2, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.SLOT_UNMOUNTABLE)
    }

    @Test
    fun `zero-slot assembly is incomplete and can't be mounted`() {
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "empty")).id!!
        val bikeId = newBike()
        val ex = assertThrows<ServiceException> { mountingAssemblyService.mountAssembly(assemblyId, bikeId, t2, emptyList()) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_INCOMPLETE)
    }

    // --- dismountAssembly --------------------------------------------------------------------

    @Test
    fun `dismountAssembly closes the assembly mounting and every governed member mounting`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        val mounted = mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val result = mountingAssemblyService.dismountAssembly(f.assemblyId, t2)
        assertThat(result.assemblyMounting.dismountedAt).isEqualTo(t2)
        assertThat(result.changes.closed.single().componentId).isEqualTo(f.componentId)
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().dismountedAt).isEqualTo(t2)
        assertThat(mounted.assemblyMounting.id).isEqualTo(result.assemblyMounting.id)

        val exNotMounted = assertThrows<ServiceException> { mountingAssemblyService.dismountAssembly(f.assemblyId, t2) }
        assertThat(exNotMounted.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_NOT_MOUNTED)
    }

    // --- planMount reports empty slots and evictions without changing anything ---------------

    @Test
    fun `planMount reports empty slots and evictions - changes nothing`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "partial")).id!!
        assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "empty-slot", componentTypeId = typeId, validFrom = t1)
        )
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val occupant = newComponent(typeId)
        mountingService.mount(bikeId, mountPointId, occupant, t1)

        val plan = mountingAssemblyService.planMount(assemblyId, bikeId, t2)
        assertThat(plan.slots.single().state).isEqualTo(DomainPlannedSlotState.EMPTY)
        assertThat(plan.mountable).isFalse()

        // occupant mounting is untouched by planning
        assertThat(mountingService.getMountings(occupant, null, null, null).single().dismountedAt).isNull()
    }

    // --- addMember / removeMember ------------------------------------------------------------

    @Test
    fun `addMember while unmounted just records composition - removeMember ends it`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "compose")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val componentId = newComponent(typeId)

        val changes = mountingAssemblyService.addMember(assemblyId, componentId, slotId, t1, mountPointId = null)
        assertThat(changes.created).isEmpty()
        assertThat(assemblyService.getAssembly(assemblyId, Instant.now()).slots.single().memberComponentId).isEqualTo(componentId)

        val removeChanges = mountingAssemblyService.removeMember(componentId, t2)
        assertThat(removeChanges.closed).isEmpty()
        assertThat(assemblyService.getAssembly(assemblyId, Instant.now()).slots.single().memberComponentId).isNull()
    }

    @Test
    fun `addMember accepts a directly-mounted component into an unmounted assembly - CE-0106`() {
        val typeId = newType()
        val componentId = newComponent(typeId)
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        mountingService.mount(bikeId, mountPointId, componentId, t1)

        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "spare")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!

        val changes = mountingAssemblyService.addMember(assemblyId, componentId, slotId, t2, mountPointId = null)
        assertThat(changes.created).isEmpty()
        assertThat(assemblyService.getAssembly(assemblyId, Instant.now()).slots.single().memberComponentId).isEqualTo(componentId)
        // still directly mounted on the bike - adding to the unmounted assembly did not dismount it
        val activeMounting = mountingService.getMountings(componentId, null, null, null).single()
        assertThat(activeMounting.dismountedAt).isNull()
        assertThat(activeMounting.mountPointId).isEqualTo(mountPointId)
    }

    @Test
    fun `later mounting the assembly at the member's same mount point adopts it - CE-0106`() {
        val typeId = newType()
        val componentId = newComponent(typeId)
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        mountingService.mount(bikeId, mountPointId, componentId, t1)

        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "spare")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        mountingAssemblyService.addMember(assemblyId, componentId, slotId, t2, mountPointId = null)

        val result = mountingAssemblyService.mountAssembly(assemblyId, bikeId, Instant.parse("2024-03-01T00:00:00Z"), emptyList())
        assertThat(result.changes.created).isEmpty() // adopted, not re-created
        val adopted = mountingService.getMountings(componentId, null, null, null).single()
        assertThat(adopted.assemblyMountingId).isEqualTo(result.assemblyMounting.id)
        // adoption makes the mounting governed - no longer directlyMounted
        assertThat(componentService.getComponent(componentId).directlyMounted).isFalse()
    }

    @Test
    fun `later mounting the assembly while the member is directly mounted elsewhere is rejected - CE-0106`() {
        val typeId = newType()
        val componentId = newComponent(typeId)
        val elsewhereBike = newBike()
        val elsewhereMountPoint = newMountPoint(elsewhereBike, typeId)
        mountingService.mount(elsewhereBike, elsewhereMountPoint, componentId, t1)

        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "spare")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        mountingAssemblyService.addMember(assemblyId, componentId, slotId, t2, mountPointId = null)

        val targetBike = newBike()
        newMountPoint(targetBike, typeId)
        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.mountAssembly(assemblyId, targetBike, Instant.parse("2024-03-01T00:00:00Z"), emptyList())
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE)
        // untouched - still mounted on the original bike
        assertThat(mountingService.getMountings(componentId, null, null, null).single().dismountedAt).isNull()
    }

    @Test
    fun `addMember mountPointId is rejected while the assembly is not mounted`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "compose")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val componentId = newComponent(typeId)
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(assemblyId, componentId, slotId, t1, mountPointId)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_DATA_INVALID)
    }

    @Test
    fun `addMember while mounted also mounts the member - removeMember dismounts it`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val secondTypeId = newType()
        val secondSlotId = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "second", componentTypeId = secondTypeId, validFrom = t1)
        ).id!!
        val secondMountPoint = newMountPoint(bikeId, secondTypeId)
        val secondComponent = newComponent(secondTypeId)

        val addChanges = mountingAssemblyService.addMember(f.assemblyId, secondComponent, secondSlotId, t2, mountPointId = null)
        assertThat(addChanges.created.single().mountPointId).isEqualTo(secondMountPoint)

        val removeChanges = mountingAssemblyService.removeMember(secondComponent, Instant.parse("2024-03-01T00:00:00Z"))
        assertThat(removeChanges.closed.single().componentId).isEqualTo(secondComponent)
    }

    @Test
    fun `addMember rejects a type-mismatched component, and an already-member component`() {
        val f = fixture()
        val typeId = f.typeId
        val otherTypeId = newType()

        val wrongType = newComponent(otherTypeId)
        val exType = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(f.assemblyId, wrongType,
                assemblyService.createAssemblySlot(f.assemblyId,
                    DomainAssemblySlot(assemblyId = f.assemblyId, name = "s2", componentTypeId = typeId, validFrom = t1)).id!!,
                t1, null)
        }
        assertThat(exType.getError()).isEqualTo(ErrorCodesDomain.TYPE_MISMATCH)

        val anotherComponent = newComponent(typeId)
        jdbc.update("UPDATE assembly_membership SET member_to = ? WHERE component_id = ? AND member_to IS NULL",
            java.sql.Timestamp.from(t1.plusSeconds(1)), f.componentId)
        // f.componentId's membership is now closed at t1+1s, but it's still "already member" logic uses activeAt null check;
        // re-seed an active membership elsewhere for anotherComponent to prove ALREADY_MEMBER
        val elsewhereAssembly = assemblyService.createAssembly(DomainAssembly(name = "elsewhere")).id!!
        val elsewhereSlot = assemblyService.createAssemblySlot(
            elsewhereAssembly, DomainAssemblySlot(assemblyId = elsewhereAssembly, name = "slot", componentTypeId = typeId, validFrom = t1)
        ).id!!
        jdbc.update("INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            anotherComponent, elsewhereSlot, java.sql.Timestamp.from(t1))
        val newSlot = assemblyService.createAssemblySlot(
            f.assemblyId, DomainAssemblySlot(assemblyId = f.assemblyId, name = "s3", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val exAlreadyMember = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(f.assemblyId, anotherComponent, newSlot, t1, null)
        }
        assertThat(exAlreadyMember.getError()).isEqualTo(ErrorCodesDomain.ALREADY_MEMBER)
    }

    // --- addMember occupied-slot swap (CE-0105) -----------------------------------------------

    @Test
    fun `addMember on an occupied unmounted slot swaps - old membership closed, new active, no mounting side effects`() {
        val f = fixture()
        val newMember = newComponent(f.typeId)

        val changes = mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t2, null)

        assertThat(changes.created).isEmpty()
        assertThat(changes.closed).isEmpty()
        assertThat(changes.membershipChanges).containsExactlyInAnyOrder(
            DomainMembershipChange(f.componentId, f.slotId, DomainMembershipAction.REMOVED, t2),
            DomainMembershipChange(newMember, f.slotId, DomainMembershipAction.ADDED, t2),
        )
        assertThat(assemblyService.getAssembly(f.assemblyId, Instant.now()).slots.single().memberComponentId).isEqualTo(newMember)
        assertThat(assemblyService.getMemberships(null, f.componentId, null).single().memberTo).isEqualTo(t2)
    }

    @Test
    fun `addMember swap on an unmounted assembly leaves the new member's own direct mounting untouched - CE-0106`() {
        val f = fixture()
        val newMember = newComponent(f.typeId)
        val elsewhereBike = newBike()
        val elsewhereMountPoint = newMountPoint(elsewhereBike, f.typeId)
        mountingService.mount(elsewhereBike, elsewhereMountPoint, newMember, t1)

        val changes = mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t2, null)

        assertThat(changes.membershipChanges).containsExactlyInAnyOrder(
            DomainMembershipChange(f.componentId, f.slotId, DomainMembershipAction.REMOVED, t2),
            DomainMembershipChange(newMember, f.slotId, DomainMembershipAction.ADDED, t2),
        )
        val newMemberMounting = mountingService.getMountings(newMember, null, null, null).single()
        assertThat(newMemberMounting.dismountedAt).isNull()
        assertThat(newMemberMounting.mountPointId).isEqualTo(elsewhereMountPoint)
        assertThat(componentService.getComponent(newMember).directlyMounted).isTrue()
    }

    @Test
    fun `addMember on an occupied mounted slot swaps - occupant's governed mounting closes, new one is created`() {
        val f = fixture()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val newMember = newComponent(f.typeId)
        val changes = mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t2, null)

        assertThat(changes.closed.single().componentId).isEqualTo(f.componentId)
        assertThat(changes.created.single()).satisfies({
            assertThat(it.componentId).isEqualTo(newMember)
            assertThat(it.mountPointId).isEqualTo(mountPointId)
        })
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().dismountedAt).isEqualTo(t2)
        val newMounting = mountingService.getMountings(newMember, null, null, null).single()
        assertThat(newMounting.dismountedAt).isNull()
        assertThat(newMounting.mountPointId).isEqualTo(mountPointId)
    }

    @Test
    fun `addMember swap strictly before the occupant's memberFrom is backdated`() {
        val f = fixture()
        val newMember = newComponent(f.typeId)

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t1.minusSeconds(1), null)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_BACKDATED)
    }

    @Test
    fun `addMember swap at exactly the occupant's memberFrom is backdated - GiST adjacency boundary`() {
        val f = fixture()
        val newMember = newComponent(f.typeId)

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t1, null)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_BACKDATED)
    }

    @Test
    fun `a swap whose new member fails leaves the occupant's membership and governed mounting intact`() {
        val f = fixture()
        val bikeId = newBike()
        newMountPoint(bikeId, f.typeId)
        mountingAssemblyService.mountAssembly(f.assemblyId, bikeId, t1, emptyList())

        val newMember = newComponent(f.typeId)
        val elsewhereBike = newBike()
        val elsewhereMountPoint = newMountPoint(elsewhereBike, f.typeId)
        mountingService.mount(elsewhereBike, elsewhereMountPoint, newMember, t1)

        val ex = assertThrows<ServiceException> {
            mountingAssemblyService.addMember(f.assemblyId, newMember, f.slotId, t2, null)
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MEMBER_MOUNTED_ELSEWHERE)

        assertThat(assemblyService.getAssembly(f.assemblyId, Instant.now()).slots.single().memberComponentId).isEqualTo(f.componentId)
        assertThat(mountingService.getMountings(f.componentId, null, null, null).single().dismountedAt).isNull()
    }

    // --- getMemberships (CE-0105) --------------------------------------------------------------

    @Test
    fun `getMemberships filters by slot or component, orders memberFrom desc, includes closed rows, requires a filter`() {
        val f = fixture()
        val second = newComponent(f.typeId)
        mountingAssemblyService.addMember(f.assemblyId, second, f.slotId, t2, null) // swaps, closing f.componentId's membership

        val bySlot = assemblyService.getMemberships(f.slotId, null, null)
        assertThat(bySlot.map { it.componentId }).containsExactly(second, f.componentId)
        assertThat(bySlot.first().assemblyId).isEqualTo(f.assemblyId)
        assertThat(bySlot.last().memberTo).isEqualTo(t2)
        assertThat(bySlot.first().memberTo).isNull()

        val byComponent = assemblyService.getMemberships(null, f.componentId, null)
        assertThat(byComponent).singleElement().extracting { it.componentId }.isEqualTo(f.componentId)

        val ex = assertThrows<ServiceException> { assemblyService.getMemberships(null, null, null) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_MEMBERSHIP_FILTER_REQUIRED)
    }
}
