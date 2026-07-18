package de.cyclingsir.cetrack.common.security

import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

/**
 * With no CETRACK_EDIT_PIN configured (the test default - see application.yaml),
 * mutating requests must behave exactly as before CE-0118: no credential required.
 */
class EditGateDisabledIT : PostgreSQLContainerIT() {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var editGateFilterRegistration: FilterRegistrationBean<EditGateFilter>

    private val mvc by lazy {
        val builder: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        val configured: DefaultMockMvcBuilder = builder.addFilters(editGateFilterRegistration.filter!!)
        configured.build()
    }

    @Test
    fun `mutating requests succeed without any credential when the gate is disabled`() {
        val bikeBody = """{"name":"IT bike ${UUID.randomUUID()}"}"""

        mvc.perform(post("/bikes").contentType(MediaType.APPLICATION_JSON).content(bikeBody))
            .andExpect(status().isCreated)
    }
}
