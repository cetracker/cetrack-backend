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
    fun `fresh CUET baseline applies cleanly on PostgreSQL`() {
        val applied = flyway.info().applied()
        assertThat(applied).isNotEmpty
        assertThat(applied.map { it.version?.version }).containsExactly("1.0", "1.1", "1.2")
        assertThat(applied.all { it.state.isApplied }).isTrue()
    }
}
