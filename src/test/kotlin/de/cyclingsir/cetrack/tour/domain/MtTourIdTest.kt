package de.cyclingsir.cetrack.tour.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

class MtTourIdTest {

    @Test
    fun `genMtId reproduces the decoded MT algorithm vector`() {
        // From CE-0060: 2026-05-28T17:21:00Z + distance 18397 → "2026528172118397"
        val result = genMtId(Instant.parse("2026-05-28T17:21:00Z"), 18397)
        assertEquals("2026528172118397", result)
    }

    @Test
    fun `genMtId uses no leading zeros (Short toString semantics)`() {
        // month=1, day=5, hour=8, minute=3, distance=10000 → "2024" + "1" + "5" + "8" + "3" + "10000"
        val result = genMtId(Instant.parse("2024-01-05T08:03:00Z"), 10000)
        assertEquals("2024158310000", result)
    }

    @Test
    fun `genMtId returns non-null non-blank for FIT create path`() {
        val result = genMtId(Instant.parse("2025-06-15T12:30:00Z"), 42000)
        assertNotNull(result)
        assert(result.isNotBlank())
    }

    @Test
    fun `genMtId returns non-null non-blank for MANUAL create path`() {
        val result = genMtId(Instant.parse("2023-03-20T09:00:00Z"), 5000)
        assertNotNull(result)
        assert(result.isNotBlank())
    }
}
