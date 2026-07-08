package de.cyclingsir.cetrack.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/**
 * CE-0043: boots with the demo-data profile so Spring's sql.init applies
 * data-demo.sql through the real startup path (after Flyway) - the same
 * ordering the demo profile relies on. Uses postgres-it for the datasource
 * wiring, deliberately NOT the `demo` group whose postgres profile would
 * fight @ServiceConnection.
 *
 * Own container (not PostgreSQLContainerIT's shared one): the seed TRUNCATEs
 * every table - by design for the demo profile, but it must not wipe state
 * other test contexts left in the shared container mid-suite.
 */
@SpringBootTest
@ActiveProfiles("postgres-it", "demo-data")
@Tag("integration")
class DemoDataIT {

    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17").apply { start() }
    }

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource

    private fun count(sql: String): Int = jdbc.queryForObject(sql, Int::class.java)!!

    @Test
    fun `demo data seeds the announced dataset`() {
        assertThat(count("select count(*) from bike")).isEqualTo(4)
        assertThat(count("select count(*) from component_assembly")).isEqualTo(8)
        assertThat(count("select count(*) from component")).isEqualTo(74)
        assertThat(count("select count(*) from tour")).isEqualTo(560)
        assertThat(count("select count(*) from maintenance_task")).isEqualTo(5)
        assertThat(count("select count(*) from maintenance_event")).isGreaterThan(20)
        // cross-mounting stories are present: assemblies moved across bikes ...
        assertThat(count("select count(distinct bike_id) from assembly_mounting where assembly_id in (select assembly_id from assembly_mounting group by assembly_id having count(distinct bike_id) > 1)")).isEqualTo(2)
        // ... and components moved across assemblies
        assertThat(count("""
            select count(*) from (
              select m.component_id from assembly_membership m
              join assembly_slot s on s.id = m.assembly_slot_id
              group by m.component_id having count(distinct s.assembly_id) > 1
            ) moved""")).isGreaterThanOrEqualTo(3)
        assertThat(count("select count(*) from component where retirement_kind = 'sold'")).isGreaterThanOrEqualTo(3)
        assertThat(count("select count(*) from component where retirement_kind = 'scrapped'")).isGreaterThanOrEqualTo(10)
    }

    @Test
    fun `active-uniqueness invariants hold`() {
        assertThat(count("select count(*) from (select mount_point_id from mounting where dismounted_at is null group by mount_point_id having count(*) > 1) x")).isZero()
        assertThat(count("select count(*) from (select component_id from mounting where dismounted_at is null group by component_id having count(*) > 1) x")).isZero()
        assertThat(count("select count(*) from (select component_id from assembly_membership where member_to is null group by component_id having count(*) > 1) x")).isZero()
        assertThat(count("select count(*) from (select assembly_slot_id from assembly_membership where member_to is null group by assembly_slot_id having count(*) > 1) x")).isZero()
        assertThat(count("select count(*) from (select assembly_id from assembly_mounting where dismounted_at is null group by assembly_id having count(*) > 1) x")).isZero()
    }

    @Test
    fun `governed mountings stay within their assembly mounting and retirement closes intervals`() {
        assertThat(count("""
            select count(*) from mounting m
            join assembly_mounting am on am.id = m.assembly_mounting_id
            where m.mounted_at < am.mounted_at
               or (am.dismounted_at is not null and (m.dismounted_at is null or m.dismounted_at > am.dismounted_at))""")).isZero()
        assertThat(count("""
            select count(*) from mounting m join component c on c.id = m.component_id
            where c.retired_at is not null and (m.dismounted_at is null or m.dismounted_at > c.retired_at)""")).isZero()
        assertThat(count("""
            select count(*) from assembly_membership am join component c on c.id = am.component_id
            where c.retired_at is not null and (am.member_to is null or am.member_to > c.retired_at)""")).isZero()
        assertThat(count("""
            select count(*) from mounting m
            join mount_point mp on mp.id = m.mount_point_id
            join bike b on b.id = mp.bike_id
            where b.retired_at is not null and (m.dismounted_at is null or m.dismounted_at > b.retired_at)""")).isZero()
    }

    @Test
    fun `re-applying the script is idempotent - TRUNCATE header refreshes the data`() {
        ScriptUtils.executeSqlScript(
            dataSource.connection,
            EncodedResource(ClassPathResource("data-demo.sql"), Charsets.UTF_8)
        )
        assertThat(count("select count(*) from tour")).isEqualTo(560)
        assertThat(count("select count(*) from bike")).isEqualTo(4)
    }
}
