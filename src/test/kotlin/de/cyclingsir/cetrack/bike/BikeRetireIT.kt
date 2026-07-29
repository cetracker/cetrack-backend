package de.cyclingsir.cetrack.bike

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class BikeRetireIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var wac: WebApplicationContext
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val mvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(wac).build() }

    @Test
    fun `retire round-trip - 200 with retiredAt, repeat conflicts, unknown bike is 404`() {
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
        val body = """{"at":"2024-06-01T00:00:00Z","kind":"scrapped"}"""

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

    @Test
    fun `all eight retirement kinds round-trip over REST against real Postgres`() {
        val kinds = listOf("scrapped", "sold", "gifted", "broken", "lost", "stolen", "wornOut", "other")
        for (kind in kinds) {
            val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
            mvc.perform(
                post("/bikes/$bikeId/action/retire")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"at":"2025-01-01T00:00:00Z","kind":"$kind","note":"note for $kind"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.retirementKind").value(kind))
                .andExpect(jsonPath("$.retirementNote").value("note for $kind"))

            mvc.perform(get("/bikes/$bikeId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.retirementKind").value(kind))
                .andExpect(jsonPath("$.retirementNote").value("note for $kind"))
        }
    }

    @Test
    fun `correctRetirement replaces kind and note, rejects a bike that is not retired`() {
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!

        mvc.perform(
            post("/bikes/$bikeId/action/correctRetirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"scrapped"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NOT_RETIRED"))

        mvc.perform(
            post("/bikes/$bikeId/action/retire")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"at":"2024-06-01T00:00:00Z","kind":"scrapped","note":"scrapped it"}""")
        ).andExpect(status().isOk)

        mvc.perform(
            post("/bikes/$bikeId/action/correctRetirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"gifted","note":"actually gifted"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retirementKind").value("gifted"))
            .andExpect(jsonPath("$.retirementNote").value("actually gifted"))
            .andExpect(jsonPath("$.retiredAt").value("2024-06-01T00:00:00Z"))

        // full replacement: an omitted note clears the existing one
        mvc.perform(
            post("/bikes/$bikeId/action/correctRetirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"gifted"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retirementNote").doesNotExist())
    }

    @Test
    fun `legacy retired bike with no kind serialises without error and can be corrected`() {
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
        jdbc.update("UPDATE bike SET retired_at = ? WHERE id = ?", java.sql.Timestamp.from(java.time.Instant.parse("2020-01-01T00:00:00Z")), bikeId)

        mvc.perform(get("/bikes/$bikeId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retiredAt").value("2020-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.retirementKind").doesNotExist())

        mvc.perform(
            post("/bikes/$bikeId/action/correctRetirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"other","note":"reason lost to history"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retirementKind").value("other"))

        assertThat(bikeService.getBike(bikeId).retirementKind).isNotNull()
    }
}
