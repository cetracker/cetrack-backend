package de.cyclingsir.cetrack.part.domain

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Initially created on 25.03.2025.
 */
class DomainPartTest {

    private lateinit var domainPart: DomainPart

    @BeforeEach
    fun init() {
        domainPart = DomainPart(
            UUID.randomUUID(),
            name = "wheel A",
            boughtAt = null,
            retiredAt = null,
            partTypeRelations = emptyList(),
            createdAt = null
        )
    }

    @Test
    fun `test returned list is immutable`() {
        val partTypeRelations = domainPart.partTypeRelations
        assertTrue(partTypeRelations is List)
    }

}
