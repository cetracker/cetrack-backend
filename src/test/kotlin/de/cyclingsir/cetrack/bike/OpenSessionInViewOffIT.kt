package de.cyclingsir.cetrack.bike

import de.cyclingsir.cetrack.bike.domain.BikeCompositionService
import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.domain.DomainMountPoint
import de.cyclingsir.cetrack.catalog.domain.CatalogService
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

/**
 * CE-0079 acceptance: with `spring.jpa.open-in-view=false` (no OSIV warning at
 * startup) a full HTTP request that reads entities and serializes the response
 * completes without a LazyInitializationException. The test is deliberately not
 * @Transactional so no ambient session masks the failure the way OSIV would.
 */
class OpenSessionInViewOffIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var bikeService: BikeService
    @Autowired private lateinit var compositionService: BikeCompositionService
    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var wac: WebApplicationContext

    private val mvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(wac).build() }

    @Test
    fun `GET mountPoints serializes without an open session`() {
        val bikeId = bikeService.addBike(DomainBike(model = "bike-${UUID.randomUUID()}")).id!!
        val typeId = catalogService.addComponentType(DomainComponentType(name = "type-${UUID.randomUUID()}")).id!!
        compositionService.addMountPoint(DomainMountPoint(bikeId = bikeId, componentTypeId = typeId, name = "front"))

        mvc.perform(get("/bikes/$bikeId/mountPoints"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("front"))
    }
}
