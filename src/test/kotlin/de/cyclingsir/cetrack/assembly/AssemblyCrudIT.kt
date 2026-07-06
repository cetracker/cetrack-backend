package de.cyclingsir.cetrack.assembly

import de.cyclingsir.cetrack.assembly.domain.AssemblyService
import de.cyclingsir.cetrack.assembly.domain.DomainAssembly
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblySlot
import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class AssemblyCrudIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var assemblyService: AssemblyService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var componentService: ComponentService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    @Test
    fun `assembly round trip - create, modify, delete`() {
        val created = assemblyService.createAssembly(DomainAssembly(name = "Rear Wheel"))
        assertThat(created.id).isNotNull()
        assertThat(created.complete).isTrue() // vacuously - no slots yet
        assertThat(created.mounted).isFalse()

        val modified = assemblyService.modifyAssembly(created.id!!, created.copy(name = "Rear Wheel Complete"))
        assertThat(modified.name).isEqualTo("Rear Wheel Complete")

        assemblyService.deleteAssembly(created.id)
        assertThat(assemblyService.getAssemblies().map { it.id }).doesNotContain(created.id)

        val exGone = assertThrows<ServiceException> { assemblyService.getAssembly(created.id, Instant.now()) }
        assertThat(exGone.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_NOT_FOUND)
    }

    @Test
    fun `assembly must have a name and rejects unknown ids`() {
        val exBlank = assertThrows<ServiceException> { assemblyService.createAssembly(DomainAssembly(name = "  ")) }
        assertThat(exBlank.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_DATA_INVALID)

        val exNotFound = assertThrows<ServiceException> {
            assemblyService.modifyAssembly(UUID.randomUUID(), DomainAssembly(name = "x"))
        }
        assertThat(exNotFound.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_NOT_FOUND)
    }

    @Test
    fun `slot round trip and completeness reflects active membership at time`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "Rear Wheel")).id!!
        val slot = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "disc", componentTypeId = typeId, validFrom = t1)
        )
        assertThat(slot.id).isNotNull()

        val incomplete = assemblyService.getAssembly(assemblyId, Instant.now())
        assertThat(incomplete.slots).hasSize(1)
        assertThat(incomplete.complete).isFalse()

        val componentId = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "disc-1")).id!!
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            componentId, slot.id, Timestamp.from(t1)
        )
        val complete = assemblyService.getAssembly(assemblyId, Instant.now())
        assertThat(complete.complete).isTrue()
        assertThat(complete.slots.single().memberComponentId).isEqualTo(componentId)

        val modifiedSlot = assemblyService.modifyAssemblySlot(assemblyId, slot.id!!, slot.copy(name = "disc-renamed"))
        assertThat(modifiedSlot.name).isEqualTo("disc-renamed")

        // membership history blocks slot deletion
        val exInUse = assertThrows<ServiceException> { assemblyService.deleteAssemblySlot(assemblyId, slot.id) }
        assertThat(exInUse.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_SLOT_IN_USE)
    }

    @Test
    fun `assembly with membership or mounting history can't be deleted`() {
        val typeId = newType()
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "Rear Wheel")).id!!
        val slotId = assemblyService.createAssemblySlot(
            assemblyId, DomainAssemblySlot(assemblyId = assemblyId, name = "disc", componentTypeId = typeId, validFrom = t1)
        ).id!!
        val componentId = componentService.addComponent(DomainComponent(componentTypeId = typeId, label = "disc-1")).id!!
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from) VALUES (?, ?, ?)",
            componentId, slotId, Timestamp.from(t1)
        )

        val ex = assertThrows<ServiceException> { assemblyService.deleteAssembly(assemblyId) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_IN_USE)
    }

    @Test
    fun `assembly mounting history is readable`() {
        assemblyService.createAssembly(DomainAssembly(name = "Rear Wheel")).id!!
        val assemblyId = assemblyService.createAssembly(DomainAssembly(name = "Front Wheel")).id!!
        assertThat(assemblyService.getAssemblyMountings(assemblyId)).isEmpty()

        val exNotFound = assertThrows<ServiceException> { assemblyService.getAssemblyMountings(UUID.randomUUID()) }
        assertThat(exNotFound.getError()).isEqualTo(ErrorCodesDomain.ASSEMBLY_NOT_FOUND)
    }
}
