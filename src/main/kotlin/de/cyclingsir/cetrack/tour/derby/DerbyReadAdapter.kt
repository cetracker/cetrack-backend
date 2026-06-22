package de.cyclingsir.cetrack.tour.derby

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class DerbyReadAdapter {

    data class ReadResult(val dbVersion: Int, val rows: List<DomainMTTour>)

    fun read(tourBookDir: Path, bikeUuids: List<String>): ReadResult {
        require(bikeUuids.isNotEmpty()) { "bikeUuids must not be empty" }
        val dbUrl = "jdbc:derby:${tourBookDir.toAbsolutePath()};readOnly=true"
        try {
            return DriverManager.getConnection(dbUrl).use { conn ->
                val dbVersion = readDbVersion(conn)
                val rows = readTours(conn, bikeUuids)
                ReadResult(dbVersion, rows)
            }
        } catch (e: ServiceException) {
            throw e
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.DERBY_SCHEMA_INCOMPATIBLE, "Derby read failed: ${e.message}")
        } finally {
            shutdownDerby(tourBookDir)
        }
    }

    private fun readDbVersion(conn: java.sql.Connection): Int {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("""SELECT VERSION FROM "USER".DBVERSION""").use { rs ->
                check(rs.next()) { "DBVERSION table is empty" }
                return rs.getInt(1)
            }
        }
    }

    private fun readTours(conn: java.sql.Connection, bikeUuids: List<String>): List<DomainMTTour> {
        val placeholders = bikeUuids.joinToString(",") { "?" }
        val sql = EXPORT_QUERY.format(placeholders)
        conn.prepareStatement(sql).use { stmt ->
            bikeUuids.forEachIndexed { i, uuid -> stmt.setString(i + 1, uuid) }
            stmt.executeQuery().use { rs ->
                val rows = mutableListOf<DomainMTTour>()
                while (rs.next()) {
                    rows += DomainMTTour(
                        MTTOURID = rs.getString("MTTOURID"),
                        TITLE = rs.getString("TITLE") ?: "",
                        DISTANCE = rs.getInt("DISTANCE"),
                        DURATIONMOVING = rs.getLong("DURATIONMOVING"),
                        TIMEELAPSEDDEVICE = rs.getLong("TIMEELAPSEDDEVICE").takeUnless { rs.wasNull() },
                        TIMERECORDEDDEVICE = rs.getLong("TIMERECORDEDDEVICE").takeUnless { rs.wasNull() },
                        STARTTIMESTAMP = rs.getLong("STARTTIMESTAMP"),
                        STARTYEAR = rs.getShort("STARTYEAR"),
                        STARTMONTH = rs.getShort("STARTMONTH"),
                        STARTDAY = rs.getShort("STARTDAY"),
                        TOURALTUP = rs.getInt("TOURALTUP"),
                        TOURALTDOWN = rs.getInt("TOURALTDOWN"),
                        POWERTOTAL = rs.getLong("POWERTOTAL"),
                        bikeId = rs.getString("BIKEID")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    )
                }
                return rows
            }
        }
    }

    // Derby emits an expected SQLException on shutdown (XJ015 = engine, 08006 = single DB).
    private fun shutdownDerby(tourBookDir: Path) {
        try {
            DriverManager.getConnection("jdbc:derby:${tourBookDir.toAbsolutePath()};shutdown=true")
        } catch (e: SQLException) {
            if (e.sqlState !in setOf("XJ015", "08006")) {
                logger.warn { "Unexpected Derby shutdown SQLState ${e.sqlState}: ${e.message}" }
            }
        }
    }

    companion object {
        private val EXPORT_QUERY = """
            SELECT
                TD.TOURID                       AS MTTOURID,
                TD.STARTYEAR,
                TD.STARTMONTH,
                TD.STARTDAY,
                TD.TOURTITLE                    AS TITLE,
                TD.TOURSTARTTIME                AS STARTTIMESTAMP,
                TD.TOURDISTANCE                 AS DISTANCE,
                TD.TOURALTUP,
                TD.TOURALTDOWN,
                TD.TOURDEVICETIME_ELAPSED       AS TIMEELAPSEDDEVICE,
                TD.TOURCOMPUTEDTIME_MOVING      AS DURATIONMOVING,
                TD.TOURDEVICETIME_RECORDED      AS TIMERECORDEDDEVICE,
                TD.POWER_TOTALWORK              AS POWERTOTAL,
                TT.NAME                         AS BIKEID
            FROM "USER".TOURDATA TD
            JOIN "USER".TOURDATA_TOURTAG DTT ON DTT.TOURDATA_TOURID = TD.TOURID
            JOIN "USER".TOURTAG TT           ON TT.TAGID = DTT.TOURTAG_TAGID
            WHERE TD.TOURPERSON_PERSONID = 0
              AND TD.TOURTYPE_TYPEID IN (0, 1, 2, 4, 113)
              AND TT.NAME IN (%s)
            ORDER BY TD.TOURSTARTTIME ASC
        """.trimIndent()
    }
}
