package de.cyclingsir.cetrack.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppSecurityConfigurationTest {

    @Test
    fun `blank PIN disables the gate`() {
        assertFalse(AppSecurityConfiguration(editPin = "").gateEnabled)
        assertFalse(AppSecurityConfiguration(editPin = "   ").gateEnabled)
    }

    @Test
    fun `configured PIN enables the gate and is trimmed`() {
        val config = AppSecurityConfiguration(editPin = "  123456  ")
        assertTrue(config.gateEnabled)
        assertEquals("123456", config.pin)
    }
}
