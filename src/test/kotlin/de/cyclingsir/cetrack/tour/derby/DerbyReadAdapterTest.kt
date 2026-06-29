package de.cyclingsir.cetrack.tour.derby

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DerbyReadAdapterTest {

    private lateinit var tempDir: Path
    private lateinit var tourBookDir: Path
    private val adapter = DerbyReadAdapter()

    companion object {
        const val FIXTURE = "mytourbook-fixture/tourbook.tar.bz2"
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
        val BIKE_B: UUID = UUID.fromString("b2222222-0002-0002-0002-000000000002")
        const val FIRST_BIKE_A_TOUR = "9000000000001"
        const val AMBIGUOUS_TOUR = "9000000000025"
        const val FOREIGN_TOUR = "9000000000026"
        const val UNTAGGED_TOUR = "9000000000028"
        const val WRONG_PERSON_TOUR = "9000000000029"
        const val WRONG_TYPE_TOUR = "9000000000030"
        const val PERSON_ID = 0
        val TOUR_TYPE_IDS : List<Int> = listOf(0, 1, 2, 4, 113)
    }

    @BeforeAll
    fun unpackFixture() {
        tempDir = Files.createTempDirectory("derby-adapter-test")
        val stream = DerbyReadAdapterTest::class.java.classLoader.getResourceAsStream(FIXTURE)!!
        TarArchiveInputStream(BZip2CompressorInputStream(stream.buffered())).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val dest = tempDir.resolve(entry.name)
                    Files.createDirectories(dest.parent)
                    Files.copy(tar, dest)
                }
                entry = tar.nextEntry
            }
        }
        tourBookDir = Files.walk(tempDir)
            .filter { Files.exists(it.resolve("service.properties")) }
            .findFirst()
            .orElseThrow { IllegalStateException("No tourbook dir in fixture") }
    }

    @AfterAll
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    // #7
    @Test
    fun `adapter reads DBVERSION from the fixture`() {
        val result = adapter.read(tourBookDir, listOf(BIKE_A.toString()), PERSON_ID, TOUR_TYPE_IDS)
        assertEquals(59, result.dbVersion)
    }

    // #8
    @Test
    fun `export query returns rows only for tracked bike UUIDs`() {
        val result = adapter.read(tourBookDir, listOf(BIKE_A.toString(), BIKE_B.toString()), PERSON_ID, TOUR_TYPE_IDS)
        assertTrue(result.rows.isNotEmpty())
        assertTrue(result.rows.all { it.bikeId == BIKE_A || it.bikeId == BIKE_B },
            "all rows must be tagged with a tracked bike")
        assertTrue(result.rows.any { it.MTTOURID == FIRST_BIKE_A_TOUR && it.bikeId == BIKE_A },
            "first bikeA tour must appear with correct bikeId")
    }

    // #9
    @Test
    fun `tour tagged with two tracked bikes yields two rows`() {
        val result = adapter.read(tourBookDir, listOf(BIKE_A.toString(), BIKE_B.toString()), PERSON_ID, TOUR_TYPE_IDS)
        val ambiguousRows = result.rows.filter { it.MTTOURID == AMBIGUOUS_TOUR }
        assertEquals(2, ambiguousRows.size, "ambiguous tour must appear once per matched bike tag")
        val bikeIds = ambiguousRows.map { it.bikeId }.toSet()
        assertEquals(setOf(BIKE_A, BIKE_B), bikeIds)
    }

    // #10
    @Test
    fun `untagged and foreign-tagged tours are excluded`() {
        val result = adapter.read(tourBookDir, listOf(BIKE_A.toString(), BIKE_B.toString()), PERSON_ID, TOUR_TYPE_IDS)
        val ids = result.rows.map { it.MTTOURID }.toSet()
        assertFalse(ids.contains(FOREIGN_TOUR), "foreign-tagged tour must be excluded")
        assertFalse(ids.contains(UNTAGGED_TOUR), "untagged tour must be excluded")
        assertFalse(ids.contains(WRONG_PERSON_TOUR), "wrong-person tour must be excluded")
    }

    // #12
    @Test
    fun `wrong-type tour is excluded by tour-type filter`() {
        val result = adapter.read(tourBookDir, listOf(BIKE_A.toString(), BIKE_B.toString()), PERSON_ID, TOUR_TYPE_IDS)
        assertFalse(result.rows.any { it.MTTOURID == WRONG_TYPE_TOUR },
            "wrong-type tour must be excluded by TOURTYPE_TYPEID filter")
    }

    // #13
    @Test
    fun `incompatible schema surfaces DERBY_SCHEMA_INCOMPATIBLE`() {
        // Files.createTempDirectory creates the dir; Derby create=true requires a non-existent path
        val parentDir = Files.createTempDirectory("wrong-schema-parent")
        val dbDir = parentDir.resolve("wrongdb")  // does not exist yet — Derby creates it
        try {
            DriverManager.getConnection("jdbc:derby:${dbDir.toAbsolutePath()};create=true").use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("""CREATE SCHEMA "USER"""")
                    stmt.execute("""CREATE TABLE "USER".DBVERSION (VERSION INT)""")
                    stmt.execute("""INSERT INTO "USER".DBVERSION VALUES (59)""")
                    // Intentionally omit TOURDATA — the export query will throw
                }
            }
            try {
                DriverManager.getConnection("jdbc:derby:${dbDir.toAbsolutePath()};shutdown=true")
            } catch (e: SQLException) { /* expected XJ015 / 08006 */ }

            val ex = assertThrows<ServiceException> {
                adapter.read(dbDir, listOf(BIKE_A.toString()), PERSON_ID, TOUR_TYPE_IDS)
            }
            assertEquals(ErrorCodesDomain.DERBY_SCHEMA_INCOMPATIBLE, ex.getError())
        } finally {
            parentDir.toFile().deleteRecursively()
        }
    }

    // #14
    @Test
    fun `second read on same path succeeds after Derby shutdown`() {
        val result1 = adapter.read(tourBookDir, listOf(BIKE_A.toString()), PERSON_ID, TOUR_TYPE_IDS)
        assertNotNull(result1)
        val result2 = adapter.read(tourBookDir, listOf(BIKE_A.toString()), PERSON_ID, TOUR_TYPE_IDS)
        assertEquals(result1.dbVersion, result2.dbVersion)
        assertEquals(result1.rows.size, result2.rows.size)
    }
}
