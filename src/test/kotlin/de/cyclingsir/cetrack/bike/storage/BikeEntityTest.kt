package de.cyclingsir.cetrack.bike.storage

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

private const val BIKE_MODEL_I = "raceBike"
private const val BIKE_MODEL_II = "gravelBike"

/**
 * Initially created on 25.03.2025.
 */
class BikeEntityTest {

    private lateinit var rawBikeEntity: BikeEntity
    private val id = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        rawBikeEntity = BikeEntity(id, BIKE_MODEL_I)
    }

    @Test
    fun `test constructor`() {
        Assertions.assertEquals(id, rawBikeEntity.id)
        Assertions.assertEquals(BIKE_MODEL_I, rawBikeEntity.model)
    }

    @Test
    fun `test get and set model`() {
        @SuppressWarnings("NP_NULL_ON_SOME_PATH") // lateinit -> false positive
        rawBikeEntity.model = BIKE_MODEL_II
        Assertions.assertEquals(BIKE_MODEL_II, rawBikeEntity.model)
    }
}
