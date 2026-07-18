package de.cyclingsir.cetrack.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Dispatch target for the filter tests below - a request that reaches this
 * controller has been let through by EditGateFilter; a 404 for an unmapped
 * path (e.g. /auth/unlock, /actuator/loggers) still proves the filter didn't
 * intercept it with a 401/429.
 */
@RestController
private class ProbeController {
    @GetMapping("/probe") fun get() = "ok"
    @PostMapping("/probe") fun post() = "ok"
}

class EditGateFilterTest {

    private val objectMapper = ObjectMapper()
    private val gatedConfig = AppSecurityConfiguration(editPin = "123456")

    private fun mvcFor(config: AppSecurityConfiguration, guard: PinLockoutGuard, tokenService: EditTokenService = EditTokenService(config)): MockMvc {
        val builder = MockMvcBuilders.standaloneSetup(ProbeController())
        val configured: org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder =
            builder.addFilters(EditGateFilter(config, tokenService, guard, objectMapper))
        return configured.build()
    }

    @Test
    fun `gate off passes mutating requests through`() {
        val mvc = mvcFor(AppSecurityConfiguration(editPin = ""), PinLockoutGuard())
        mvc.perform(post("/probe")).andExpect(status().isOk)
    }

    @Test
    fun `GET always passes regardless of gate state`() {
        val mvc = mvcFor(gatedConfig, PinLockoutGuard())
        mvc.perform(get("/probe")).andExpect(status().isOk)
    }

    @Test
    fun `mutating request without credential is rejected`() {
        val mvc = mvcFor(gatedConfig, PinLockoutGuard())
        mvc.perform(post("/probe"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `valid bearer token passes`() {
        val tokenService = EditTokenService(gatedConfig)
        val mvc = mvcFor(gatedConfig, PinLockoutGuard(), tokenService)
        val (token, _) = tokenService.mint()

        mvc.perform(post("/probe").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `valid PIN header passes`() {
        val mvc = mvcFor(gatedConfig, PinLockoutGuard())
        mvc.perform(post("/probe").header("Cetrack-Edit-Pin", "123456"))
            .andExpect(status().isOk)
    }

    @Test
    fun `invalid PIN header is rejected and 5 consecutive failures trigger the cooldown`() {
        val guard = PinLockoutGuard(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
        val mvc = mvcFor(gatedConfig, guard)

        repeat(4) {
            mvc.perform(post("/probe").header("Cetrack-Edit-Pin", "000000"))
                .andExpect(status().isUnauthorized)
        }
        mvc.perform(post("/probe").header("Cetrack-Edit-Pin", "000000"))
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
    }

    @Test
    fun `unlock endpoint is exempt from the gate`() {
        // standalone MockMvc leaves servletPath empty unless told otherwise;
        // the real deployment strips context-path "/api" so servletPath IS the match target (see EditGateFilter).
        val mvc = mvcFor(gatedConfig, PinLockoutGuard())
        mvc.perform(post("/auth/unlock").servletPath("/auth/unlock")).andExpect(status().isNotFound)
    }

    @Test
    fun `actuator POSTs are exempt from the gate`() {
        val mvc = mvcFor(gatedConfig, PinLockoutGuard())
        mvc.perform(post("/actuator/loggers").servletPath("/actuator/loggers")).andExpect(status().isNotFound)
    }
}
