package de.cyclingsir.cetrack.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Registers EditGateFilter with an explicit low-precedence order so it runs
 * after RequestLoggingFilterConfig's logging filter (which registers with no
 * explicit order and would otherwise race it).
 */
@Configuration
class EditGateFilterConfig {
    @Bean
    fun editGateFilter(
        config: AppSecurityConfiguration,
        tokenService: EditTokenService,
        lockoutGuard: PinLockoutGuard
    ): FilterRegistrationBean<EditGateFilter> =
        // Own ObjectMapper rather than the app's autoconfigured one: the filter only
        // ever writes the plain {code, message} Error shape (no java.time fields), so
        // it doesn't need any of the app's Jackson modules, and this keeps it decoupled
        // from whatever bean the web-autoconfiguration module happens to expose.
        FilterRegistrationBean(EditGateFilter(config, tokenService, lockoutGuard, ObjectMapper())).apply {
            order = Ordered.LOWEST_PRECEDENCE
        }
}
