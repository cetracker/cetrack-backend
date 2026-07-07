package de.cyclingsir.cetrack.bike

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
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class BikeCompositionIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var mountingService: MountingService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    @Test
    fun `mount point round trip with position`() {
        val bikeId = bikeService.addBike(DomainBike(name = "Roadie")).id!!
        val typeId = newType()
        val positionId = catalogService.addPosition(DomainPosition(name = "front-${UUID.randomUUID()}")).id

        val created = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, positionId = positionId,
                name = "front tire", mandatory = true)
        )
        assertThat(created.id).isNotNull()
        assertThat(created.positionId).isEqualTo(positionId)
        assertThat(created.mandatory).isTrue()

        val modified = compositionService.modifyMountPoint(
            bikeId, created.id!!, created.copy(name = "front tyre", mandatory = false)
        )
        assertThat(modified.name).isEqualTo("front tyre")
        assertThat(modified.mandatory).isFalse()

        compositionService.deleteMountPoint(bikeId, created.id)
        assertThat(compositionService.getMountPoints(bikeId)).isEmpty()

        val exUnknownBike = assertThrows<ServiceException> { compositionService.getMountPoints(UUID.randomUUID()) }
        assertThat(exUnknownBike.getError()).isEqualTo(ErrorCodesDomain.BIKE_NOT_FOUND)
    }

    @Test
    fun `type is locked while a mounting is active and history blocks delete`() {
        val bikeId = bikeService.addBike(DomainBike(model = "Gravel")).id!!
        val typeId = newType()
        val mountPoint = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp")
        )
        val componentId = componentService.addComponent(
            DomainComponent(componentTypeId = typeId, label = "comp")
        ).id!!
        mountingService.mount(bikeId, mountPoint.id!!, componentId, Instant.parse("2024-01-01T00:00:00Z"))

        val exType = assertThrows<ServiceException> {
            compositionService.modifyMountPoint(bikeId, mountPoint.id, mountPoint.copy(componentTypeId = newType()))
        }
        assertThat(exType.getError()).isEqualTo(ErrorCodesDomain.MOUNT_POINT_IN_USE)

        // renaming stays possible while mounted
        compositionService.modifyMountPoint(bikeId, mountPoint.id, mountPoint.copy(name = "renamed"))

        mountingService.dismount(componentId, Instant.parse("2024-02-01T00:00:00Z"))
        val exDelete = assertThrows<ServiceException> {
            compositionService.deleteMountPoint(bikeId, mountPoint.id)
        }
        assertThat(exDelete.getError()).isEqualTo(ErrorCodesDomain.MOUNT_POINT_IN_USE)
    }

    @Test
    fun `slot mappings are readable and resettable`() {
        val bikeId = bikeService.addBike(DomainBike(model = "MTB")).id!!
        val typeId = newType()
        val mountPointId = compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "mp")
        ).id!!
        val assemblyId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        jdbc.update("INSERT INTO component_assembly (id, name) VALUES (?, ?)", assemblyId, "assembly")
        jdbc.update(
            "INSERT INTO assembly_slot (id, assembly_id, component_type_id, name, valid_from) VALUES (?, ?, ?, ?, now())",
            slotId, assemblyId, typeId, "slot"
        )
        jdbc.update(
            "INSERT INTO slot_mapping (assembly_slot_id, bike_id, mount_point_id) VALUES (?, ?, ?)",
            slotId, bikeId, mountPointId
        )

        val mappings = compositionService.getSlotMappings(bikeId)
        assertThat(mappings).hasSize(1)
        assertThat(mappings.single().assemblySlotId).isEqualTo(slotId)

        compositionService.deleteSlotMapping(bikeId, mappings.single().id!!)
        assertThat(compositionService.getSlotMappings(bikeId)).isEmpty()

        val exGone = assertThrows<ServiceException> {
            compositionService.deleteSlotMapping(bikeId, mappings.single().id!!)
        }
        assertThat(exGone.getError()).isEqualTo(ErrorCodesDomain.SLOT_MAPPING_NOT_FOUND)
    }

    @Test
    fun `bike must be identifiable and can't be deleted while referenced`() {
        val exBlank = assertThrows<ServiceException> { bikeService.addBike(DomainBike()) }
        assertThat(exBlank.getError()).isEqualTo(ErrorCodesDomain.BIKE_NOT_IDENTIFIABLE)

        val bikeId = bikeService.addBike(DomainBike(model = "Tourer")).id!!
        compositionService.addMountPoint(
            DomainMountPoint(bikeId = bikeId, componentTypeId = newType(), name = "mp")
        )
        val exDelete = assertThrows<ServiceException> { bikeService.deleteBike(bikeId) }
        assertThat(exDelete.getError()).isEqualTo(ErrorCodesDomain.BIKE_HAS_FOREIGN_KEY_CONSTRAINT)
    }
}
