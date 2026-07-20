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
import de.cyclingsir.cetrack.component.domain.DomainComponentStatus
import de.cyclingsir.cetrack.component.domain.DomainRetirementKind
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class MountDismountIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val t2: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val t3: Instant = Instant.parse("2024-03-01T00:00:00Z")

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    private fun newBike(): UUID =
        bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!

    private fun newMountPoint(bikeId: UUID, typeId: UUID): UUID =
        compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp-${UUID.randomUUID()}")
        ).id!!

    private fun newComponent(typeId: UUID): UUID =
        componentService.addComponent(
            DomainComponent(componentTypeId = typeId, label = "comp-${UUID.randomUUID()}")
        ).id!!

    private fun seedUnmountedAssemblyMembership(componentId: UUID, typeId: UUID): UUID {
        val assemblyId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        jdbc.update("INSERT INTO component_assembly (id, name) VALUES (?, ?)", assemblyId, "assembly")
        jdbc.update(
            "INSERT INTO assembly_slot (id, assembly_id, component_type_id, name, valid_from) VALUES (?, ?, ?, ?, now())",
            slotId, assemblyId, typeId, "slot"
        )
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, now())",
            componentId, slotId
        )
        return assemblyId
    }

    @Test
    fun `mount creates the fact and auto-dismounts the previous occupant`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val first = newComponent(typeId)
        val second = newComponent(typeId)

        val firstChanges = mountingService.mount(bikeId, mountPointId, first, t1)
        assertThat(firstChanges.created).hasSize(1)
        assertThat(firstChanges.closed).isEmpty()
        assertThat(firstChanges.created.single().mountPointName).isNotBlank()
        assertThat(firstChanges.created.single().bikeId).isEqualTo(bikeId)
        assertThat(componentService.getComponent(first).status).isEqualTo(DomainComponentStatus.MOUNTED)

        val secondChanges = mountingService.mount(bikeId, mountPointId, second, t2)
        assertThat(secondChanges.closed).hasSize(1)
        assertThat(secondChanges.closed.single().componentId).isEqualTo(first)
        assertThat(secondChanges.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(componentService.getComponent(first).status).isEqualTo(DomainComponentStatus.IN_STOCK)
        assertThat(componentService.getComponent(second).status).isEqualTo(DomainComponentStatus.MOUNTED)
    }

    @Test
    fun `mount auto-dismounts the component's own previous mounting`() {
        val typeId = newType()
        val bikeId = newBike()
        val mpA = newMountPoint(bikeId, typeId)
        val mpB = newMountPoint(bikeId, typeId)
        val component = newComponent(typeId)

        mountingService.mount(bikeId, mpA, component, t1)
        val changes = mountingService.mount(bikeId, mpB, component, t2)

        assertThat(changes.closed).hasSize(1)
        assertThat(changes.closed.single().mountPointId).isEqualTo(mpA)
        assertThat(mountingService.getMountings(component, null, null, activeAt = t3))
            .singleElement()
            .extracting { it.mountPointId }.isEqualTo(mpB)
    }

    @Test
    fun `mount rejects type mismatch, retired component, retired bike`() {
        val typeId = newType()
        val otherTypeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)

        val wrongType = newComponent(otherTypeId)
        val exType = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, wrongType, t1) }
        assertThat(exType.getError()).isEqualTo(ErrorCodesDomain.TYPE_MISMATCH)

        val retired = newComponent(typeId)
        componentService.retireComponent(retired, t1, DomainRetirementKind.SOLD)
        val exRetired = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, retired, t2) }
        assertThat(exRetired.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_RETIRED)

        val component = newComponent(typeId)
        jdbc.update("UPDATE bike SET retired_at = now() WHERE id = ?", bikeId)
        val exBike = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, component, t2) }
        assertThat(exBike.getError()).isEqualTo(ErrorCodesDomain.BIKE_RETIRED)

        // a mount point of a different bike is not found on this one
        val otherBike = newBike()
        val exForeignMp = assertThrows<ServiceException> {
            mountingService.mount(otherBike, mountPointId, component, t2)
        }
        assertThat(exForeignMp.getError()).isEqualTo(ErrorCodesDomain.MOUNT_POINT_NOT_FOUND)
    }

    @Test
    fun `member of a not-mounted assembly triggers the guided choice - mounted assembly does not`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)

        val member = newComponent(typeId)
        val assemblyId = seedUnmountedAssemblyMembership(member, typeId)
        val ex = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, member, t1) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_MEMBER_GUIDED_CHOICE)
        assertThat(ex.details).isNotNull()
        assertThat(ex.details!!["assemblyId"]).isEqualTo(assemblyId)
        assertThat(ex.details!!["options"] as List<*>)
            .containsExactly("MOUNT_ASSEMBLY_INSTEAD", "REMOVE_MEMBERSHIP_THEN_MOUNT")

        // same membership, but the assembly is mounted -> §1/§2 territory, no guided choice
        jdbc.update(
            "INSERT INTO assembly_mounting (assembly_id, bike_id, mounted_at) VALUES (?, ?, ?)",
            assemblyId, bikeId, java.sql.Timestamp.from(t1)
        )
        val changes = mountingService.mount(bikeId, mountPointId, member, t2)
        assertThat(changes.created).hasSize(1)
    }

    @Test
    fun `backdated times are service-level 400s - CE-0120 before-T occupant collides instead`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val first = newComponent(typeId)
        val second = newComponent(typeId)

        mountingService.mount(bikeId, mountPointId, first, t2)

        // t1 predates the occupant's start - occupant isn't active at T, so this isn't "the
        // occupant" at all; the insert collides with the occupant's still-open interval on GiST.
        val exBefore = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, second, t1) }
        assertThat(exBefore.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_OVERLAP)
        // exactly at the occupant's start - it IS the occupant at T, boundary rejected
        val exSame = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, second, t2) }
        assertThat(exSame.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_BACKDATED)

        val exDismount = assertThrows<ServiceException> { mountingService.dismount(first, t2) }
        assertThat(exDismount.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_BACKDATED)
    }

    // --- CE-0120: occupancy resolved at mount time T, not now ---------------------------------

    @Test
    fun `mount evicts a closed occupant containing T, shortening its history - CE-0120`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val first = newComponent(typeId)
        val second = newComponent(typeId)

        mountingService.mount(bikeId, mountPointId, first, t1)
        mountingService.dismount(first, t3)

        val changes = mountingService.mount(bikeId, mountPointId, second, t2)
        assertThat(changes.closed.single().componentId).isEqualTo(first)
        assertThat(changes.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(mountingService.getMountings(first, null, null, null).single().dismountedAt).isEqualTo(t2)
    }

    @Test
    fun `mount shortens the component's own closed mounting elsewhere containing T - CE-0120`() {
        val typeId = newType()
        val bikeId = newBike()
        val mpA = newMountPoint(bikeId, typeId)
        val mpB = newMountPoint(bikeId, typeId)
        val component = newComponent(typeId)

        mountingService.mount(bikeId, mpA, component, t1)
        mountingService.dismount(component, t3)

        val changes = mountingService.mount(bikeId, mpB, component, t2)
        assertThat(changes.closed.single().mountPointId).isEqualTo(mpA)
        assertThat(changes.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(changes.created.single().mountPointId).isEqualTo(mpB)
    }

    @Test
    fun `mount at exactly an occupant's closed dismountedAt is adjacent, not evicted - CE-0120`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val first = newComponent(typeId)
        val second = newComponent(typeId)

        mountingService.mount(bikeId, mountPointId, first, t1)
        mountingService.dismount(first, t2)

        val changes = mountingService.mount(bikeId, mountPointId, second, t2)
        assertThat(changes.closed).isEmpty()
        assertThat(changes.created.single().componentId).isEqualTo(second)
    }

    @Test
    fun `mount rejects replacing a closed governed occupant - correct the assembly history instead - CE-0120`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)

        val assemblyId = seedUnmountedAssemblyMembership(newComponent(typeId), typeId)
        val governedComponent = newComponent(typeId)
        val assemblyMountingId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO assembly_mounting (id, assembly_id, bike_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?, ?)",
            assemblyMountingId, assemblyId, bikeId, java.sql.Timestamp.from(t1), java.sql.Timestamp.from(t3)
        )
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, assembly_mounting_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?, ?)",
            governedComponent, mountPointId, assemblyMountingId, java.sql.Timestamp.from(t1), java.sql.Timestamp.from(t3)
        )

        val replacement = newComponent(typeId)
        val ex = assertThrows<ServiceException> { mountingService.mount(bikeId, mountPointId, replacement, t2) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_GOVERNED)
        assertThat(mountingService.getMountings(governedComponent, null, null, null).single().dismountedAt).isEqualTo(t3)
    }

    @Test
    fun `dismount closes, rejects unmounted and assembly-governed components`() {
        val typeId = newType()
        val bikeId = newBike()
        val mountPointId = newMountPoint(bikeId, typeId)
        val component = newComponent(typeId)

        val exNotMounted = assertThrows<ServiceException> { mountingService.dismount(component, t1) }
        assertThat(exNotMounted.getError()).isEqualTo(ErrorCodesDomain.NOT_MOUNTED)

        mountingService.mount(bikeId, mountPointId, component, t1)
        val changes = mountingService.dismount(component, t2)
        assertThat(changes.closed.single().dismountedAt).isEqualTo(t2)
        assertThat(componentService.getComponent(component).status).isEqualTo(DomainComponentStatus.IN_STOCK)

        // governed mounting: seed provenance, then dismount must point to the assembly
        val assemblyId = seedUnmountedAssemblyMembership(newComponent(typeId), typeId)
        val governedComponent = newComponent(typeId)
        val assemblyMountingId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO assembly_mounting (id, assembly_id, bike_id, mounted_at) VALUES (?, ?, ?, ?)",
            assemblyMountingId, assemblyId, bikeId, java.sql.Timestamp.from(t1)
        )
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, assembly_mounting_id, mounted_at) VALUES (?, ?, ?, ?)",
            governedComponent, mountPointId, assemblyMountingId, java.sql.Timestamp.from(t2)
        )
        val exGoverned = assertThrows<ServiceException> { mountingService.dismount(governedComponent, t3) }
        assertThat(exGoverned.getError()).isEqualTo(ErrorCodesDomain.MOUNTING_GOVERNED)
    }
}
