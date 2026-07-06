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
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipAction
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * ADR-0001 §2 (CE-0086 plan ruling 2b): the three-way partition of direct
 * mount() against assembly-governed state. Assembly tables are seeded via raw
 * SQL (no ComponentAssembly aggregate exists in the mounting module).
 */
class MountingGovernedPropagationIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    private fun newBike(): UUID = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!

    private fun newMountPoint(bikeId: UUID, typeId: UUID): UUID =
        compositionService.addMountPoint(DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp-${UUID.randomUUID()}")).id!!

    private fun newComponent(typeId: UUID): UUID =
        componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "comp-${UUID.randomUUID()}")).id!!

    /** A mounted assembly with one governed member at [mountPointId]. */
    private fun seedMountedAssemblyMember(bikeId: UUID, mountPointId: UUID, typeId: UUID, memberComponentId: UUID): UUID {
        val assemblyId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val assemblyMountingId = UUID.randomUUID()
        jdbc.update("INSERT INTO component_assembly (id, name) VALUES (?, ?)", assemblyId, "assembly")
        jdbc.update(
            "INSERT INTO assembly_slot (id, assembly_id, component_type_id, name, valid_from) VALUES (?, ?, ?, ?, now())",
            slotId, assemblyId, typeId, "slot"
        )
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            memberComponentId, slotId, Timestamp.from(t1)
        )
        jdbc.update(
            "INSERT INTO assembly_mounting (id, assembly_id, bike_id, mounted_at) VALUES (?, ?, ?, ?)",
            assemblyMountingId, assemblyId, bikeId, Timestamp.from(t1)
        )
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, assembly_mounting_id, mounted_at) VALUES (?, ?, ?, ?)",
            memberComponentId, mountPointId, assemblyMountingId, Timestamp.from(t1)
        )
        return slotId
    }

    @Test
    fun `governed occupant of a mounted assembly propagates membership to the replacement`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val governedMember = newComponent(typeId)
        val slotId = seedMountedAssemblyMember(bikeId, mountPointId, typeId, governedMember)
        val replacement = newComponent(typeId)

        val changes = mountingService.mount(bikeId, mountPointId, replacement, t2)

        assertThat(changes.closed.single().componentId).isEqualTo(governedMember)
        assertThat(changes.created.single().componentId).isEqualTo(replacement)
        assertThat(changes.created.single().assemblyMountingId).isNotNull()
        assertThat(changes.membershipChanges).hasSize(2)
        assertThat(changes.membershipChanges).anySatisfy {
            assertThat(it.componentId).isEqualTo(governedMember)
            assertThat(it.assemblySlotId).isEqualTo(slotId)
            assertThat(it.action).isEqualTo(DomainMembershipAction.REMOVED)
        }
        assertThat(changes.membershipChanges).anySatisfy {
            assertThat(it.componentId).isEqualTo(replacement)
            assertThat(it.assemblySlotId).isEqualTo(slotId)
            assertThat(it.action).isEqualTo(DomainMembershipAction.ADDED)
        }

        // the old member's membership is closed, the replacement's is active
        val activeMember = jdbc.queryForObject(
            "SELECT component_id FROM assembly_membership WHERE assembly_slot_id = ? AND member_to IS NULL",
            UUID::class.java, slotId
        )
        assertThat(activeMember).isEqualTo(replacement)
    }

    @Test
    fun `governed occupant propagation rejects when the replacement is already a member elsewhere`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val governedMember = newComponent(typeId)
        seedMountedAssemblyMember(bikeId, mountPointId, typeId, governedMember)

        // the replacement must be an active member of a MOUNTED assembly but hold no governed
        // Mounting of its own - a member of a not-mounted assembly hits the earlier ADR-0001 §3
        // guided-choice guard instead, and one holding its own governed mounting elsewhere hits
        // the "elsewhere" MOUNTING_GOVERNED guard instead (both different scenarios)
        val replacement = newComponent(typeId)
        val otherAssemblyId = UUID.randomUUID()
        val otherSlotId = UUID.randomUUID()
        jdbc.update("INSERT INTO component_assembly (id, name) VALUES (?, ?)", otherAssemblyId, "other")
        jdbc.update(
            "INSERT INTO assembly_slot (id, assembly_id, component_type_id, name, valid_from) VALUES (?, ?, ?, ?, now())",
            otherSlotId, otherAssemblyId, typeId, "other-slot"
        )
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            replacement, otherSlotId, Timestamp.from(t1)
        )
        jdbc.update(
            "INSERT INTO assembly_mounting (assembly_id, bike_id, mounted_at) VALUES (?, ?, ?)",
            otherAssemblyId, newBike(), Timestamp.from(t1)
        )

        val ex = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, replacement, t2) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ALREADY_MEMBER)
    }

    @Test
    fun `a component holding an active governed mounting elsewhere is rejected before the occupant is touched`() {
        val typeId = newType()
        val bikeId = newBike()
        val governedElsewhereMp = newMountPoint(bikeId, typeId)
        val governedComponent = newComponent(typeId)
        seedMountedAssemblyMember(bikeId, governedElsewhereMp, typeId, governedComponent)

        val targetMountPoint = newMountPoint(bikeId, typeId)
        val directOccupant = newComponent(typeId)
        mountingService.mount(bikeId, targetMountPoint, directOccupant, t1)

        val ex = assertThrows<ServiceException> { mountingService.mount(bikeId, targetMountPoint, governedComponent, t2) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_GOVERNED)

        // the occupant at the target must be untouched - the whole mount was rejected
        assertThat(mountingService.getMountings(directOccupant, null, null, null).single().dismountedAt).isNull()
    }
}
