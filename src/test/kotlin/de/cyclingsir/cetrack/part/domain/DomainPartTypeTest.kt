package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.DomainBike
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Initially created on 25.03.2025.
 */
class DomainPartTypeTest {

    private lateinit var domainPartType: DomainPartType

    @MockK
    private lateinit var mockedDomainBike: DomainBike

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        domainPartType = DomainPartType(
            id = UUID.randomUUID(),
            name = "front wheel",
            mandatory = true,
            partTypeRelations = emptyList(),
            bike = mockedDomainBike,
            createdAt = null
        )
    }

    @Test
    fun `test partTypeRelations list is immutable`() {
        val relations = domainPartType.partTypeRelations
        assertTrue(relations is List)
    }

}
