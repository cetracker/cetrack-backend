package de.cyclingsir.cetrack.catalog

import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.catalog.domain.DomainPosition
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class CatalogCrudIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var service: CatalogService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `component type round trip`() {
        val created = service.addComponentType(DomainComponentType(name = "tire-${UUID.randomUUID()}", description = "rubber"))
        assertThat(created.id).isNotNull()
        assertThat(created.createdAt).isNotNull()

        val modified = service.modifyComponentType(created.id!!, created.copy(description = "hoop of rubber"))
        assertThat(modified.description).isEqualTo("hoop of rubber")

        service.deleteComponentType(created.id)
        assertThrows<ServiceException> { service.getComponentType(created.id) }
    }

    @Test
    fun `duplicate component type name is rejected`() {
        val name = "chain-${UUID.randomUUID()}"
        service.addComponentType(DomainComponentType(name = name))
        val ex = assertThrows<ServiceException> {
            service.addComponentType(DomainComponentType(name = name))
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_TYPE_DATA_INVALID)
    }

    @Test
    fun `referenced component type can't be deleted`() {
        val type = service.addComponentType(DomainComponentType(name = "brake-${UUID.randomUUID()}"))
        jdbc.update(
            "INSERT INTO component (component_type_id, label) VALUES (?, ?)",
            type.id, "test brake"
        )
        val ex = assertThrows<ServiceException> { service.deleteComponentType(type.id!!) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.COMPONENT_TYPE_IN_USE)
    }

    @Test
    fun `duplicate position name is rejected`() {
        val name = "front-${UUID.randomUUID()}"
        service.addPosition(DomainPosition(name = name))
        val ex = assertThrows<ServiceException> { service.addPosition(DomainPosition(name = name)) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.POSITION_DATA_INVALID)
    }

    @Test
    fun `position round trip and delete guard`() {
        val position = service.addPosition(DomainPosition(name = "front-${UUID.randomUUID()}"))
        assertThat(position.id).isNotNull()

        val type = service.addComponentType(DomainComponentType(name = "wheel-${UUID.randomUUID()}"))
        val bikeId = UUID.randomUUID()
        jdbc.update("INSERT INTO bike (id, model) VALUES (?, ?)", bikeId, "guard bike")
        jdbc.update(
            "INSERT INTO mount_point (bike_id, component_type_id, position_id, name) VALUES (?, ?, ?, ?)",
            bikeId, type.id, position.id, "front wheel"
        )

        val ex = assertThrows<ServiceException> { service.deletePosition(position.id!!) }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.POSITION_IN_USE)
    }
}
