package de.cyclingsir.cetrack.assembly.rest

import de.cyclingsir.cetrack.assembly.domain.AssemblyMountingService
import de.cyclingsir.cetrack.assembly.domain.AssemblyService
import de.cyclingsir.cetrack.assembly.domain.DomainAssembly
import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
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
import java.time.Instant
import java.util.UUID

/** One controller-level test pins the wiring of the generated AssembliesApi interface. */
class AssemblyControllerTest {

    private val service = mockk<AssemblyService>()
    private val mountingService = mockk<AssemblyMountingService>()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            AssemblyController(service, mountingService, AssemblyDomain2ApiMapper(MountingDomain2ApiMapper()), MountingDomain2ApiMapper())
        )
        .setControllerAdvice(CentralExceptionHandler())
        .build()

    @Test
    fun `createAssembly answers 201 with Location and body`() {
        val created = DomainAssembly(id = UUID.randomUUID(), name = "Rear Wheel")
        every { service.createAssembly(any()) } returns created

        mvc.perform(
            post("/assemblies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Rear Wheel"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/assemblies/${created.id}"))
            .andExpect(jsonPath("$.name").value("Rear Wheel"))
    }

    @Test
    fun `getAssembly binds the optional at query param`() {
        val assemblyId = UUID.randomUUID()
        val at = Instant.parse("2024-01-01T00:00:00Z")
        every { service.getAssembly(assemblyId, at) } returns DomainAssembly(id = assemblyId, name = "x")

        mvc.perform(get("/assemblies/$assemblyId").param("at", "2024-01-01T00:00:00Z"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(assemblyId.toString()))
    }

    @Test
    fun `ServiceException renders the shared Error shape through the dispatch chain`() {
        val assemblyId = UUID.randomUUID()
        every { service.deleteAssembly(assemblyId) } throws ServiceException(ErrorCodesDomain.ASSEMBLY_IN_USE)

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/assemblies/$assemblyId")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IN_USE"))
    }
}
