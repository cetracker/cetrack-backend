package de.cyclingsir.cetrack.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.infrastructure.api.model.Error
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Gates mutating `/api` requests behind the shared edit PIN (CE-0118): a
 * valid `Authorization: Bearer <token>` or `Cetrack-Edit-Pin` header is
 * required, unless the gate is disabled (blank PIN) or the path is exempt.
 * Runs as a plain filter (not a @ControllerAdvice), so it writes the
 * generated `Error` JSON directly via the injected ObjectMapper.
 */
class EditGateFilter(
    private val config: AppSecurityConfiguration,
    private val tokenService: EditTokenService,
    private val lockoutGuard: PinLockoutGuard,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (!config.gateEnabled || request.method !in MUTATING_METHODS || isExempt(request.servletPath)) {
            filterChain.doFilter(request, response)
            return
        }

        when (val result = evaluate(request)) {
            GateResult.Allowed -> filterChain.doFilter(request, response)
            GateResult.Denied -> writeError(response, 401, ErrorCodesDomain.UNAUTHORIZED, retryAfterSeconds = null)
            is GateResult.Locked -> writeError(response, 429, ErrorCodesDomain.TOO_MANY_ATTEMPTS, result.retryAfterSeconds)
        }
    }

    private fun isExempt(servletPath: String) = servletPath == UNLOCK_PATH || servletPath.startsWith(ACTUATOR_PREFIX)

    private fun evaluate(request: HttpServletRequest): GateResult {
        val bearer = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.removePrefix(BEARER_PREFIX)
            ?.takeIf { it.isNotBlank() }
        if (bearer != null && tokenService.verify(bearer)) return GateResult.Allowed

        val pinHeader = request.getHeader(PIN_HEADER) ?: return GateResult.Denied

        lockoutGuard.secondsUntilUnlocked()?.let { return GateResult.Locked(it) }

        if (!PIN_PATTERN.matches(pinHeader)) return GateResult.Denied

        if (pinHeader == config.pin) {
            lockoutGuard.recordSuccess()
            return GateResult.Allowed
        }

        lockoutGuard.recordFailure()
        return lockoutGuard.secondsUntilUnlocked()?.let { GateResult.Locked(it) } ?: GateResult.Denied
    }

    private fun writeError(response: HttpServletResponse, status: Int, error: ErrorCodesDomain, retryAfterSeconds: Long?) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        retryAfterSeconds?.let { response.setHeader(HttpHeaders.RETRY_AFTER, it.toString()) }
        objectMapper.writeValue(response.writer, Error(code = error.wireCode!!, message = error.reason.orEmpty()))
    }

    private sealed interface GateResult {
        data object Allowed : GateResult
        data object Denied : GateResult
        data class Locked(val retryAfterSeconds: Long) : GateResult
    }

    companion object {
        private val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        private val PIN_PATTERN = Regex("^[0-9]{6}$")
        private const val PIN_HEADER = "Cetrack-Edit-Pin"
        private const val BEARER_PREFIX = "Bearer "
        private const val UNLOCK_PATH = "/auth/unlock"
        private const val ACTUATOR_PREFIX = "/actuator/"
    }
}
