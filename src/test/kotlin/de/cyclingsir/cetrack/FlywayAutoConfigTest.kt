package de.cyclingsir.cetrack

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class FlywayAutoConfigTest {

    // Guards against spring-boot-starter-flyway being accidentally dropped from the build.
    // In Spring Boot 4.x FlywayAutoConfiguration lives in a separate spring-boot-flyway module
    // that is NOT a transitive dependency of spring-boot-starter-data-jpa.
    @Test
    fun `FlywayAutoConfiguration creates Flyway bean when starter is present`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DataSourceAutoConfiguration::class.java,
                    FlywayAutoConfiguration::class.java,
                )
            )
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:flyway-autoconfig-test",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.locations=classpath:db/migration/doesnotexist",
                "spring.flyway.fail-on-missing-locations=false",
            )
            .run { ctx ->
                assertThat(ctx).hasSingleBean(Flyway::class.java)
            }
    }
}
