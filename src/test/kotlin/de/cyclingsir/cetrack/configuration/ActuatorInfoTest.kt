package de.cyclingsir.cetrack.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.info.InfoEndpoint
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.SpringBootTest

/**
 * Guards the buildInfo wiring: springBoot { buildInfo() } must publish a
 * BuildProperties bean, and it must be visible through the info actuator endpoint
 * (which is exposed under the /api context-path in application.yaml).
 */
@SpringBootTest
class ActuatorInfoTest(
    @Autowired private val buildProperties: BuildProperties,
    @Autowired private val infoEndpoint: InfoEndpoint,
) {

    @Test
    fun `build info exposes the gradle version`() {
        assertThat(buildProperties.version).isNotBlank()
    }

    @Test
    fun `info endpoint includes the build contributor`() {
        val info = infoEndpoint.info()

        assertThat(info).containsKey("build")
    }
}
