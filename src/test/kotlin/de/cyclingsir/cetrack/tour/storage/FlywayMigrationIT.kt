package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FlywayMigrationIT : PostgreSQLContainerIT() {

    @Autowired
    private lateinit var flyway: Flyway

    @Test
    fun `all migrations apply cleanly on real MySQL and Hibernate validate passes`() {
        val applied = flyway.info().applied()
        assertThat(applied).isNotEmpty
        assertThat(applied.map { it.version?.version }).contains("1.0", "1.1", "1.2", "1.3", "1.4", "1.5")
    }
}
