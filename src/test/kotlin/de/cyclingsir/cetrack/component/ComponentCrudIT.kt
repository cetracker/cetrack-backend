package de.cyclingsir.cetrack.component

import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.component.domain.DomainComponentStatus
import de.cyclingsir.cetrack.component.domain.DomainRetirementKind
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class ComponentCrudIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var service: ComponentService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun newType(): UUID =
        catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!

    private fun newComponent(typeId: UUID = newType(), label: String = "comp-${UUID.randomUUID()}"): DomainComponent =
        service.addComponent(DomainComponent(componentTypeId = typeId, label = label))

    /** bike + mount point + mounting seeded raw - the mounting module arrives in a later step. */
    private fun seedMounting(componentId: UUID, typeId: UUID, dismountedAt: Instant? = null) {
        val bikeId = UUID.randomUUID()
        val mountPointId = UUID.randomUUID()
        jdbc.update("INSERT INTO bike (id, model) VALUES (?, ?)", bikeId, "seed bike")
        jdbc.update(
            "INSERT INTO mount_point (id, bike_id, component_type_id, name) VALUES (?, ?, ?, ?)",
            mountPointId, bikeId, typeId, "seed mp"
        )
        jdbc.update(
            "INSERT INTO mounting (component_id, mount_point_id, mounted_at, dismounted_at) VALUES (?, ?, ?, ?)",
            componentId, mountPointId, java.sql.Timestamp.from(Instant.parse("2024-01-01T00:00:00Z")),
            dismountedAt?.let { java.sql.Timestamp.from(it) }
        )
    }

    private fun seedMembership(componentId: UUID, typeId: UUID, memberTo: Instant? = null) {
        val assemblyId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        jdbc.update("INSERT INTO component_assembly (id, name) VALUES (?, ?)", assemblyId, "seed assembly")
        jdbc.update(
            "INSERT INTO assembly_slot (id, assembly_id, component_type_id, name, valid_from) VALUES (?, ?, ?, ?, now())",
            slotId, assemblyId, typeId, "seed slot"
        )
        jdbc.update(
            "INSERT INTO assembly_membership (component_id, assembly_slot_id, member_from, member_to) VALUES (?, ?, now(), ?)",
            componentId, slotId, memberTo?.let { java.sql.Timestamp.from(it) }
        )
    }

    @Test
    fun `create modify delete round trip, status inStock`() {
        val component = newComponent()
        assertThat(component.id).isNotNull()
        assertThat(component.status).isEqualTo(DomainComponentStatus.IN_STOCK)

        val modified = service.modifyComponent(component.id!!, component.copy(manufacturer = "Conti"))
        assertThat(modified.manufacturer).isEqualTo("Conti")

        service.deleteComponent(component.id!!)
        assertThrows<ServiceException> { service.getComponent(component.id!!) }
    }

    @Test
    fun `unknown component type is rejected as invalid data`() {
        val ex = assertThrows<ServiceException> {
            service.addComponent(DomainComponent(componentTypeId = UUID.randomUUID(), label = "orphan"))
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_DATA_INVALID)
    }

    @Test
    fun `type can't change while mounted`() {
        val typeId = newType()
        val component = newComponent(typeId)
        seedMounting(component.id!!, typeId)

        val ex = assertThrows<ServiceException> {
            service.modifyComponent(component.id!!, component.copy(componentTypeId = newType()))
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_IN_USE)

        // renaming stays possible while mounted
        val renamed = service.modifyComponent(component.id!!, component.copy(label = "renamed"))
        assertThat(renamed.label).isEqualTo("renamed")
    }

    @Test
    fun `price without currency is rejected`() {
        val ex = assertThrows<ServiceException> {
            service.addComponent(DomainComponent(componentTypeId = newType(), label = "odd", price = "10.50"))
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_PRICE_CURRENCY_MISMATCH)
    }

    @Test
    fun `status derivation - mounted, inAssembly, retired - and filters`() {
        val typeId = newType()
        val mounted = newComponent(typeId)
        seedMounting(mounted.id!!, typeId)
        val member = newComponent(typeId)
        seedMembership(member.id!!, typeId)
        val retired = newComponent(typeId)
        service.retireComponent(retired.id!!, Instant.now(), DomainRetirementKind.SOLD)
        val inStock = newComponent(typeId)

        assertThat(service.getComponent(mounted.id!!).status).isEqualTo(DomainComponentStatus.MOUNTED)
        assertThat(service.getComponent(member.id!!).status).isEqualTo(DomainComponentStatus.IN_ASSEMBLY)
        assertThat(service.getComponent(retired.id!!).status).isEqualTo(DomainComponentStatus.RETIRED)
        assertThat(service.getComponent(inStock.id!!).status).isEqualTo(DomainComponentStatus.IN_STOCK)

        val stockOnly = service.getComponents(typeId, DomainComponentStatus.IN_STOCK)
        assertThat(stockOnly.map { it.id }).containsExactly(inStock.id)
        assertThat(service.getComponents(typeId, null)).hasSize(4)
    }

    @Test
    fun `closed mounting and membership do not affect status but block delete`() {
        val typeId = newType()
        val component = newComponent(typeId)
        seedMounting(component.id!!, typeId, dismountedAt = Instant.parse("2024-02-01T00:00:00Z"))

        assertThat(service.getComponent(component.id!!).status).isEqualTo(DomainComponentStatus.IN_STOCK)

        val ex = assertThrows<ServiceException> { service.deleteComponent(component.id!!) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_IN_USE)
    }

    @Test
    fun `retire preconditions - active mounting or membership reject, success sets kind`() {
        val typeId = newType()
        val mounted = newComponent(typeId)
        seedMounting(mounted.id!!, typeId)
        val exMounted = assertThrows<ServiceException> {
            service.retireComponent(mounted.id!!, Instant.now(), DomainRetirementKind.SCRAPPED)
        }
        assertThat(exMounted.getError()).isEqualTo(ErrorCodesDomain.RETIRE_PRECONDITION_FAILED)

        val member = newComponent(typeId)
        seedMembership(member.id!!, typeId)
        val exMember = assertThrows<ServiceException> {
            service.retireComponent(member.id!!, Instant.now(), DomainRetirementKind.SCRAPPED)
        }
        assertThat(exMember.getError()).isEqualTo(ErrorCodesDomain.RETIRE_PRECONDITION_FAILED)

        val free = newComponent(typeId)
        val retired = service.retireComponent(free.id!!, Instant.parse("2025-01-01T00:00:00Z"), DomainRetirementKind.SCRAPPED)
        assertThat(retired.retirementKind).isEqualTo(DomainRetirementKind.SCRAPPED)
        assertThat(retired.retiredAt).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"))

        val exAgain = assertThrows<ServiceException> {
            service.retireComponent(free.id!!, Instant.now(), DomainRetirementKind.SOLD)
        }
        assertThat(exAgain.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_RETIRED)
    }
}
