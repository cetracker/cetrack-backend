package de.cyclingsir.cetrack

import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.junit.jupiter.api.Test

/**
 * Full context boot against the flyway-migrated PG schema with
 * `ddl-auto: validate` - the entity <-> schema drift gate (CE-0083).
 */
class MainApplicationTests : PostgreSQLContainerIT() {

    @Test
    fun contextLoads() {
        // This is empty on purpose
    }

}
