package de.cyclingsir.cetrack.common.security

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mints and verifies the stateless unlock token (CE-0118): the token is a
 * PIN-derived HMAC over its own expiry, so it needs no server-side storage
 * and a PIN change invalidates every previously issued token.
 */
@Service
class EditTokenService(
    private val config: AppSecurityConfiguration,
    private val clock: Clock = Clock.systemUTC()
) {
    fun mint(): Pair<String, Instant> {
        val expiresAt = Instant.now(clock).plus(TOKEN_TTL)
        val payload = expiresAt.epochSecond.toString().toByteArray()
        val token = "${encode(payload)}.${encode(hmac(payload))}"
        return token to expiresAt
    }

    fun verify(token: String): Boolean {
        val parts = token.split(".")
        if (parts.size != 2) return false
        val payload = decode(parts[0]) ?: return false
        val mac = decode(parts[1]) ?: return false
        if (!MessageDigest.isEqual(mac, hmac(payload))) return false
        val expiresAtEpochSecond = String(payload).toLongOrNull() ?: return false
        return !Instant.now(clock).isAfter(Instant.ofEpochSecond(expiresAtEpochSecond))
    }

    private fun hmac(payload: ByteArray): ByteArray {
        val key = MessageDigest.getInstance("SHA-256").digest("$KEY_PREFIX${config.pin}".toByteArray())
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(payload)
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String): ByteArray? =
        runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()

    companion object {
        private const val KEY_PREFIX = "CE-0118|v1|"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val TOKEN_TTL: Duration = Duration.ofDays(30)
    }
}
