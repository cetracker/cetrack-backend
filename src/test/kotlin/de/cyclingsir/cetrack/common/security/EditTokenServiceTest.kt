package de.cyclingsir.cetrack.common.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class EditTokenServiceTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `a freshly minted token verifies`() {
        val service = EditTokenService(AppSecurityConfiguration(editPin = "123456"), fixedClock)
        val (token, _) = service.mint()
        assertTrue(service.verify(token))
    }

    @Test
    fun `a tampered payload fails verification`() {
        val service = EditTokenService(AppSecurityConfiguration(editPin = "123456"), fixedClock)
        val (token, _) = service.mint()
        val (payload, mac) = token.split(".")
        val tampered = "${payload}x.$mac"
        assertFalse(service.verify(tampered))
    }

    @Test
    fun `a tampered MAC fails verification`() {
        val service = EditTokenService(AppSecurityConfiguration(editPin = "123456"), fixedClock)
        val (token, _) = service.mint()
        val (payload, mac) = token.split(".")
        val tampered = "$payload.${mac}x"
        assertFalse(service.verify(tampered))
    }

    @Test
    fun `an expired token fails verification`() {
        val mintingClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        val service = EditTokenService(AppSecurityConfiguration(editPin = "123456"), mintingClock)
        val (token, _) = service.mint()

        val afterExpiryClock = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z").plus(Duration.ofDays(30)).plusSeconds(1),
            ZoneOffset.UTC
        )
        val serviceLater = EditTokenService(AppSecurityConfiguration(editPin = "123456"), afterExpiryClock)
        assertFalse(serviceLater.verify(token))
    }

    @Test
    fun `changing the PIN invalidates previously minted tokens`() {
        val minted = EditTokenService(AppSecurityConfiguration(editPin = "123456"), fixedClock).mint().first
        val verifier = EditTokenService(AppSecurityConfiguration(editPin = "654321"), fixedClock)
        assertFalse(verifier.verify(minted))
    }

    @Test
    fun `malformed tokens fail verification`() {
        val service = EditTokenService(AppSecurityConfiguration(editPin = "123456"), fixedClock)
        assertFalse(service.verify("not-a-token"))
        assertFalse(service.verify(""))
        assertFalse(service.verify("only-one-part."))
    }
}
