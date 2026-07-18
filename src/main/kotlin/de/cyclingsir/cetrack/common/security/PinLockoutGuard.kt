package de.cyclingsir.cetrack.common.security

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Shared in-memory lockout counter for the edit PIN (CE-0118): consumed by
 * both POST /auth/unlock and the raw-PIN-header path in EditGateFilter, so a
 * client can't bypass the cooldown by switching between the two entry points.
 * Single-instance-backend assumption; no cross-replica coordination.
 */
@Component
class PinLockoutGuard(private val clock: Clock = Clock.systemUTC()) {
    @Volatile
    private var failures = 0

    @Volatile
    private var lockedUntil: Instant? = null

    @Synchronized
    fun secondsUntilUnlocked(): Long? {
        val until = lockedUntil ?: return null
        val now = Instant.now(clock)
        if (!now.isBefore(until)) {
            lockedUntil = null
            failures = 0
            return null
        }
        return Duration.between(now, until).seconds.coerceAtLeast(1)
    }

    @Synchronized
    fun recordFailure() {
        failures++
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            lockedUntil = Instant.now(clock).plusSeconds(COOLDOWN_SECONDS)
        }
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        lockedUntil = null
    }

    companion object {
        const val MAX_CONSECUTIVE_FAILURES = 5
        const val COOLDOWN_SECONDS = 60L
    }
}
