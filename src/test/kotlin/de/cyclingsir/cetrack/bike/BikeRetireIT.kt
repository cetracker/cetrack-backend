package de.cyclingsir.cetrack.bike

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class BikeRetireIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var wac: WebApplicationContext

    private val mvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(wac).build() }

    @Test
    fun `retire round-trip - 200 with retiredAt, repeat conflicts, unknown bike is 404`() {
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
        val body = """{"at":"2024-06-01T00:00:00Z"}"""

        mvc.perform(post("/bikes/$bikeId/action/retire").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retiredAt").value("2024-06-01T00:00:00Z"))

        mvc.perform(post("/bikes/$bikeId/action/retire").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("BIKE_ALREADY_RETIRED"))

        mvc.perform(post("/bikes/${UUID.randomUUID()}/action/retire")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
    }
}
