package de.cyclingsir.cetrack.common.security.rest

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.common.security.AppSecurityConfiguration
import de.cyclingsir.cetrack.common.security.EditTokenService
import de.cyclingsir.cetrack.common.security.PinLockoutGuard
import de.cyclingsir.cetrack.infrastructure.api.model.AuthStatus
import de.cyclingsir.cetrack.infrastructure.api.model.Error
import de.cyclingsir.cetrack.infrastructure.api.model.UnlockRequest
import de.cyclingsir.cetrack.infrastructure.api.model.UnlockResponse
import de.cyclingsir.cetrack.infrastructure.api.rest.AuthApi
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset

@RestController
class AuthController(
    private val config: AppSecurityConfiguration,
    private val tokenService: EditTokenService,
    private val lockoutGuard: PinLockoutGuard
) : AuthApi {

    override fun getAuthStatus(): ResponseEntity<AuthStatus> =
        ResponseEntity.ok(AuthStatus(enabled = config.gateEnabled))

    override fun unlock(@Valid @RequestBody unlockRequest: UnlockRequest): ResponseEntity<UnlockResponse> {
        if (!config.gateEnabled) {
            throw ServiceException(ErrorCodesDomain.GATE_DISABLED)
        }
        lockoutGuard.secondsUntilUnlocked()?.let { throw LockedException(it) }

        if (unlockRequest.pin != config.pin) {
            lockoutGuard.recordFailure()
            lockoutGuard.secondsUntilUnlocked()?.let { throw LockedException(it) }
            throw ServiceException(ErrorCodesDomain.INVALID_PIN)
        }
        lockoutGuard.recordSuccess()

        val (token, expiresAt) = tokenService.mint()
        return ResponseEntity.ok(UnlockResponse(token = token, expiresAt = expiresAt.atOffset(ZoneOffset.UTC)))
    }

    /**
     * Local handler (takes precedence over CentralExceptionHandler for this
     * type) so the 429 can carry the Retry-After header the shared Error
     * body has no room for.
     */
    @ExceptionHandler(LockedException::class)
    fun handleLocked(ex: LockedException): ResponseEntity<Error> =
        ResponseEntity.status(429)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(Error(code = ErrorCodesDomain.TOO_MANY_ATTEMPTS.wireCode!!, message = ErrorCodesDomain.TOO_MANY_ATTEMPTS.reason.orEmpty()))
}

class LockedException(val retryAfterSeconds: Long) : RuntimeException()
