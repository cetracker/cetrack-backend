package de.cyclingsir.cetrack.common.security.rest

import de.cyclingsir.cetrack.common.errorhandling.CentralExceptionHandler
import de.cyclingsir.cetrack.common.security.AppSecurityConfiguration
import de.cyclingsir.cetrack.common.security.EditTokenService
import de.cyclingsir.cetrack.common.security.PinLockoutGuard
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthControllerTest {

    private val gatedConfig = AppSecurityConfiguration(editPin = "123456")

    private fun mvcFor(config: AppSecurityConfiguration, guard: PinLockoutGuard = PinLockoutGuard()): MockMvc =
        MockMvcBuilders
            .standaloneSetup(AuthController(config, EditTokenService(config), guard))
            .setControllerAdvice(CentralExceptionHandler())
            .build()

    @Test
    fun `unlock with the correct PIN returns a token and expiry`() {
        val mvc = mvcFor(gatedConfig)

        mvc.perform(
            post("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.expiresAt").isNotEmpty)
    }

    @Test
    fun `unlock with the wrong PIN returns 401 INVALID_PIN`() {
        val mvc = mvcFor(gatedConfig)

        mvc.perform(
            post("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"000000"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_PIN"))
    }

    @Test
    fun `unlock while locked returns 429 with Retry-After`() {
        val guard = PinLockoutGuard(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
        val mvc = mvcFor(gatedConfig, guard)
        repeat(5) { guard.recordFailure() }

        mvc.perform(
            post("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456"}""")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
    }

    @Test
    fun `the 5th consecutive wrong PIN itself returns 429, not 401`() {
        val guard = PinLockoutGuard(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
        val mvc = mvcFor(gatedConfig, guard)

        repeat(4) {
            mvc.perform(
                post("/auth/unlock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"pin":"000000"}""")
            ).andExpect(status().isUnauthorized)
        }
        mvc.perform(
            post("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"000000"}""")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
    }

    @Test
    fun `unlock while the gate is disabled returns 409 GATE_DISABLED`() {
        val mvc = mvcFor(AppSecurityConfiguration(editPin = ""))

        mvc.perform(
            post("/auth/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GATE_DISABLED"))
    }

    @Test
    fun `status reports the gate enabled`() {
        mvcFor(gatedConfig).perform(get("/auth/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
    }

    @Test
    fun `status reports the gate disabled`() {
        mvcFor(AppSecurityConfiguration(editPin = "")).perform(get("/auth/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
    }
}
