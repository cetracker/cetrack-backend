package de.cyclingsir.cetrack.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PinLockoutGuardTest {

    @Test
    fun `not locked before any failures`() {
        assertNull(PinLockoutGuard().secondsUntilUnlocked())
    }

    @Test
    fun `5 consecutive failures trigger the cooldown`() {
        val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        val guard = PinLockoutGuard(clock)

        repeat(4) { guard.recordFailure() }
        assertNull(guard.secondsUntilUnlocked())

        guard.recordFailure()
        assertEquals(60L, guard.secondsUntilUnlocked())
    }

    @Test
    fun `success resets the counter`() {
        val guard = PinLockoutGuard()
        repeat(4) { guard.recordFailure() }
        guard.recordSuccess()
        repeat(4) { guard.recordFailure() }
        assertNull(guard.secondsUntilUnlocked())
    }

    @Test
    fun `cooldown expires after 60 seconds`() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        }
        val guard = PinLockoutGuard(clock)
        repeat(5) { guard.recordFailure() }
        assertEquals(60L, guard.secondsUntilUnlocked())

        now = now.plusSeconds(61)
        assertNull(guard.secondsUntilUnlocked())
    }
}
