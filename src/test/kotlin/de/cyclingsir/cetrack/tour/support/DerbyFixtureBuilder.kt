package de.cyclingsir.cetrack.tour.support

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

/**
 * Spec for a single MyTourbook tour row. Drives the TOURDATA + TOURDATA_TOURTAG inserts.
 *
 * Column mapping: startTimestampMs → TOURSTARTTIME, distance → TOURDISTANCE,
 * durationMoving → TOURCOMPUTEDTIME_MOVING (the columns the export query aliases back to the
 * triple that stage() uses for logical-duplicate detection).
 */
data class TourSpec(
    val mtTourId: Long,
    val bikeTags: List<UUID>,       // >1 tracked tags → AMBIGUOUS_BIKE; untracked UUID → excluded
    val startTimestampMs: Long,
    val distance: Int,
    val durationMoving: Long,
    val person: Int = 0,            // ≠0 → excluded by adapter (TOURPERSON_PERSONID filter)
    val type: Int = 0,              // not in {0,1,2,4,113} → excluded
    val title: String = "Test tour $mtTourId",
    val altUp: Int = 500,
    val altDown: Int = 480,
    val powerTotal: Long = 0L,
)

object DerbyFixtureBuilder {

    /**
     * Builds an in-memory Derby DB from [tours], shuts it down, tars it as tourbook.tar.bz2
     * layout, and returns the stream. [omitTable] skips one CREATE TABLE statement from
     * schema.sql (used to produce a schema-incompatible fixture for Group G2).
     */
    fun buildFixture(
        dbVersion: Int = 59,
        tours: List<TourSpec>,
        omitTable: String? = null,
    ): InputStream {
        val tempDir = Files.createTempDirectory("derby-fixture-builder")
        try {
            val dbPath = tempDir.resolve("tourbook")
            val dbUrl = "jdbc:derby:${dbPath.toAbsolutePath()};create=true;user=user"

            DriverManager.getConnection(dbUrl).use { conn ->
                createSchema(conn, omitTable)
                insertData(conn, dbVersion, tours, omitTable)
            }
            shutdownDerby(dbPath)

            check(Files.exists(dbPath.resolve("service.properties"))) {
                "Derby did not write service.properties — archive would be invalid"
            }

            return tarBzip2(tempDir, "tourbook")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun createSchema(conn: java.sql.Connection, omitTable: String?) {
        val sql = DerbyFixtureBuilder::class.java.classLoader
            .getResourceAsStream("mytourbook-fixture/schema.sql")!!
            .bufferedReader().readText()

        splitStatements(sql)
            .filter { stmt ->
                if (omitTable == null) true
                else !stmt.uppercase().contains("\"$omitTable\"") || stmt.uppercase().startsWith("CREATE INDEX")
            }
            .forEach { stmt ->
                runCatching { conn.createStatement().execute(stmt) }
                    .onFailure { e ->
                        // some indexes reference columns that may be fine; ignore only known-safe failures
                        if (!e.message.orEmpty().contains("already exists", ignoreCase = true)) throw e
                    }
            }
    }

    private fun splitStatements(sql: String): List<String> =
        sql.split(";")
            .map { chunk ->
                chunk.lines()
                    .filterNot { line -> line.trim().startsWith("--") }
                    .joinToString("\n")
                    .trim()
            }
            .filter { it.isNotBlank() }

    private fun insertData(conn: java.sql.Connection, dbVersion: Int, tours: List<TourSpec>, omitTable: String?) {
        conn.createStatement().execute("""INSERT INTO "USER"."DBVERSION" (VERSION) VALUES ($dbVersion)""")

        val omit = omitTable?.uppercase()
        val tagIdByUuid: Map<UUID, Long>
        if (omit != "TOURTAG") {
            val allTags: List<UUID> = tours.flatMap { it.bikeTags }.distinct()
            allTags.forEachIndexed { idx, uuid ->
                conn.createStatement().execute(
                    """INSERT INTO "USER"."TOURTAG" (TAGID, ISROOT, EXPANDTYPE, NAME, NOTES) VALUES (${idx + 1}, 1, 0, '$uuid', NULL)"""
                )
            }
            tagIdByUuid = allTags.mapIndexed { idx, uuid -> uuid to (idx + 1).toLong() }.toMap()
        } else {
            tagIdByUuid = emptyMap()
        }

        tours.forEach { t ->
            val startYear = java.time.Instant.ofEpochMilli(t.startTimestampMs)
                .atZone(java.time.ZoneOffset.UTC).year.toShort()
            val startMonth = java.time.Instant.ofEpochMilli(t.startTimestampMs)
                .atZone(java.time.ZoneOffset.UTC).monthValue.toShort()
            val startDay = java.time.Instant.ofEpochMilli(t.startTimestampMs)
                .atZone(java.time.ZoneOffset.UTC).dayOfMonth.toShort()

            conn.createStatement().execute(
                """INSERT INTO "USER"."TOURDATA"
                   (TOURID, STARTYEAR, STARTMONTH, STARTDAY, STARTHOUR, STARTMINUTE, STARTWEEK,
                    STARTDISTANCE, DISTANCE, STARTALTITUDE, STARTPULSE, DPTOLERANCE,
                    TOURDISTANCE, TOURALTUP, TOURALTDOWN, DEVICETRAVELTIME,
                    DEVICEDISTANCE, DEVICEWHEEL, DEVICEWEIGHT, DEVICETOTALUP, DEVICETOTALDOWN,
                    TOURTITLE, TOURSTARTTIME, TOURDEVICETIME_ELAPSED, TOURCOMPUTEDTIME_MOVING,
                    TOURDEVICETIME_RECORDED, POWER_TOTALWORK, TOURTYPE_TYPEID, TOURPERSON_PERSONID)
                   VALUES (${t.mtTourId}, $startYear, $startMonth, $startDay, 8, 0, 1,
                    0, 0, 200, 0, 2,
                    ${t.distance}, ${t.altUp}, ${t.altDown}, ${t.durationMoving},
                    0, 0, 0, ${t.altUp}, ${t.altDown},
                    '${t.title.replace("'", "''")}', ${t.startTimestampMs}, ${t.durationMoving}, ${t.durationMoving},
                    ${t.durationMoving}, ${t.powerTotal}, ${t.type}, ${t.person})"""
            )
            if (omit != "TOURTAG") {
                t.bikeTags.forEach { tagUuid ->
                    val tagId = tagIdByUuid[tagUuid] ?: return@forEach
                    conn.createStatement().execute(
                        """INSERT INTO "USER"."TOURDATA_TOURTAG" (TOURTAG_TAGID, TOURDATA_TOURID) VALUES ($tagId, ${t.mtTourId})"""
                    )
                }
            }
        }
    }

    private fun shutdownDerby(dbPath: Path) {
        try {
            DriverManager.getConnection("jdbc:derby:${dbPath.toAbsolutePath()};shutdown=true")
        } catch (e: SQLException) {
            if (e.sqlState !in setOf("XJ015", "08006")) {
                throw e
            }
        }
    }

    private fun tarBzip2(tempDir: Path, subdir: String): InputStream {
        val buf = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(buf)).use { tar ->
            val dbDir = tempDir.resolve(subdir)
            Files.walk(dbDir).forEach { path ->
                if (Files.isRegularFile(path)) {
                    val entryName = "$subdir/${tempDir.relativize(path)}"
                    val entry = TarArchiveEntry(path.toFile(), entryName)
                    tar.putArchiveEntry(entry)
                    Files.copy(path, tar)
                    tar.closeArchiveEntry()
                }
            }
        }
        return ByteArrayInputStream(buf.toByteArray())
    }
}
