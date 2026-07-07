package de.cyclingsir.cetrack.component.rest

import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.domain.ComponentService
import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.component.domain.DomainComponentStatus
import de.cyclingsir.cetrack.mounting.domain.MountingService
import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

/**
 * One controller-level test pins the wiring pattern of the generated *Api
 * interfaces: param binding, request mapping, 201 Location, and the wire
 * Error shape through the real Spring MVC dispatch chain.
 */
class ComponentControllerTest {

    private val service = mockk<ComponentService>()
    private val mountingService = mockk<MountingService>()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            ComponentController(service, ComponentDomain2ApiMapper(), mountingService, MountingDomain2ApiMapper())
        )
        .setControllerAdvice(CentralExceptionHandler())
        .build()

    private val typeId: UUID = UUID.randomUUID()

    private fun aComponent(id: UUID = UUID.randomUUID()) = DomainComponent(
        id = id,
        componentTypeId = typeId,
        label = "Conti GP5000 #2",
        status = DomainComponentStatus.IN_STOCK
    )

    @Test
    fun `createComponent answers 201 with Location and body`() {
        val created = aComponent()
        every { service.addComponent(any()) } returns created

        mvc.perform(
            post("/components")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"componentTypeId":"$typeId","label":"Conti GP5000 #2"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/components/${created.id}"))
            .andExpect(jsonPath("$.label").value("Conti GP5000 #2"))
            .andExpect(jsonPath("$.status").value("inStock"))
    }

    @Test
    fun `getComponents binds componentTypeId and status query params`() {
        every { service.getComponents(typeId, DomainComponentStatus.MOUNTED) } returns listOf(
            aComponent().copy(status = DomainComponentStatus.MOUNTED, directlyMounted = true)
        )

        mvc.perform(get("/components").param("componentTypeId", typeId.toString()).param("status", "mounted"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("mounted"))
            .andExpect(jsonPath("$[0].directlyMounted").value(true))
    }

    @Test
    fun `ServiceException renders the shared Error shape through the dispatch chain`() {
        val componentId = UUID.randomUUID()
        every { service.retireComponent(componentId, any(), any()) } throws
            ServiceException(ErrorCodesDomain.RETIRE_PRECONDITION_FAILED,
                "Component has an active mounting; dismount it first.")

        mvc.perform(
            post("/components/$componentId/action/retire")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"at":"2025-01-01T00:00:00Z","kind":"sold"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RETIRE_PRECONDITION_FAILED"))
            .andExpect(jsonPath("$.message").isNotEmpty)
    }
}
