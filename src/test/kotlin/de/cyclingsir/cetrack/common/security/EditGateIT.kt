package de.cyclingsir.cetrack.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

/**
 * End-to-end proof of the PIN-gated write access control (CE-0118) through
 * the real servlet stack: context-path stripping, FilterRegistrationBean
 * wiring, and the full 401 -> unlock -> retry round trip against a real
 * domain endpoint (POST /bikes), not just the filter/controller in
 * isolation (see EditGateFilterTest / AuthControllerTest). Built from the
 * real WebApplicationContext rather than @AutoConfigureMockMvc, which this
 * Spring Boot 4.1 module split no longer pulls in via spring-boot-starter-test.
 */
@TestPropertySource(properties = ["app.security.edit-pin=123456"])
class EditGateIT : PostgreSQLContainerIT() {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var editGateFilterRegistration: FilterRegistrationBean<EditGateFilter>

    private val objectMapper = ObjectMapper()

    // webAppContextSetup (unlike the unavailable @AutoConfigureMockMvc) does not
    // auto-discover registered servlet filters, so the gate filter must be added explicitly.
    private val mvc by lazy {
        val builder: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        val configured: DefaultMockMvcBuilder = builder.addFilters(editGateFilterRegistration.filter!!)
        configured.build()
    }

    @Test
    fun `GET stays open, mutating is rejected then succeeds after unlock, and 5 wrong PINs lock out`() {
        val bikeName = "IT bike ${UUID.randomUUID()}"
        val bikeBody = """{"name":"$bikeName"}"""

        mvc.perform(get("/bikes")).andExpect(status().isOk)

        mvc.perform(post("/bikes").contentType(MediaType.APPLICATION_JSON).content(bikeBody))
            .andExpect(status().isUnauthorized)

        val unlockResult = mvc.perform(
            post("/auth/unlock")
                .servletPath("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456"}""")
        ).andExpect(status().isOk).andReturn()
        val token = objectMapper.readTree(unlockResult.response.contentAsString).get("token").asText()

        mvc.perform(
            post("/bikes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bikeBody)
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isCreated)

        repeat(4) {
            mvc.perform(
                post("/auth/unlock").servletPath("/auth/unlock")
                    .contentType(MediaType.APPLICATION_JSON).content("""{"pin":"000000"}""")
            ).andExpect(status().isUnauthorized)
        }
        mvc.perform(
            post("/auth/unlock").servletPath("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON).content("""{"pin":"000000"}""")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
    }
}
