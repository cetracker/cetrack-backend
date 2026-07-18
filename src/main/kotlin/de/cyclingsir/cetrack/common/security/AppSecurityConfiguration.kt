package de.cyclingsir.cetrack.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the PIN-gated write access control (CE-0118).
 * Bound to the {@code app.security} prefix in application.yaml.
 */
@ConfigurationProperties(prefix = "app.security")
data class AppSecurityConfiguration(
    val editPin: String = ""
) {
    val pin: String get() = editPin.trim()
    val gateEnabled: Boolean get() = pin.isNotBlank()
}
