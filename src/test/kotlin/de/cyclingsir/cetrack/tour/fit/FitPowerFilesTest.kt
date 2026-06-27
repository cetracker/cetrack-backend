package de.cyclingsir.cetrack.tour.fit

import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * CE-0066 spike — power fields and native Garmin device files.
 *
 * Complements FitParserSpikeTest (Strava file, no power) with two real cycling rides
 * recorded on Garmin/Wahoo devices with power meters.
 *
 * Validates:
 *  - total_work / avg_power are present and non-zero for power-equipped rides
 *  - total_ascent / total_descent are present in native device FIT files
 *  - per-record power is accessible
 */
class FitPowerFilesTest {

    private fun parse(resource: String): Pair<List<SessionMesg>, List<RecordMesg>> {
        val sessions = mutableListOf<SessionMesg>()
        val records = mutableListOf<RecordMesg>()
        val broadcaster = MesgBroadcaster(Decode())
        broadcaster.addListener(SessionMesgListener { sessions.add(it) })
        broadcaster.addListener(RecordMesgListener { records.add(it) })
        FitPowerFilesTest::class.java.classLoader
            .getResourceAsStream(resource)!!
            .use { broadcaster.run(it) }
        return sessions to records
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class File2022 {

        private val sessions = mutableListOf<SessionMesg>()
        private val records = mutableListOf<RecordMesg>()

        @BeforeAll
        fun parse() {
            val (s, r) = parse("fit-fixture/2022-08-24-134314.fit")
            sessions += s; records += r
        }

        // startTime=1661348594000  distance=76597.8  timer=10541  elapsed=13930
        // ascent=1205  descent=1214  totalWork=1762974  avgPower=228

        @Test fun `single session`() = assertEquals(1, sessions.size)

        @Test fun `total_work present and correct`() {
            assertNotNull(sessions[0].totalWork)
            assertEquals(1_762_974L, sessions[0].totalWork!!)
        }

        @Test fun `avg_power present and correct`() {
            assertNotNull(sessions[0].avgPower)
            assertEquals(228, sessions[0].avgPower!!)
        }

        @Test fun `elevation present in native device file`() {
            assertEquals(1205, sessions[0].totalAscent, "total_ascent")
            assertEquals(1214, sessions[0].totalDescent, "total_descent")
        }

        @Test fun `per-record power accessible`() {
            val withPower = records.filter { (it.power ?: 0) > 0 }
            assertTrue(withPower.isNotEmpty(), "at least one record must carry power > 0")
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FileElemntRoam2025 {

        private val sessions = mutableListOf<SessionMesg>()
        private val records = mutableListOf<RecordMesg>()

        @BeforeAll
        fun parse() {
            val (s, r) = parse("fit-fixture/2025-06-07-060955-ELEMNT-ROAM.fit")
            sessions += s; records += r
        }

        // startTime=1749276595000  distance=124843.2  timer=22243  elapsed=34355
        // ascent=1802  descent=2982  totalWork=2581558  avgPower=184

        @Test fun `single session`() = assertEquals(1, sessions.size)

        @Test fun `total_work present and correct`() {
            assertNotNull(sessions[0].totalWork)
            assertEquals(2_581_558L, sessions[0].totalWork!!)
        }

        @Test fun `avg_power present and correct`() {
            assertNotNull(sessions[0].avgPower)
            assertEquals(184, sessions[0].avgPower!!)
        }

        @Test fun `elevation present in native device file`() {
            assertEquals(1802, sessions[0].totalAscent, "total_ascent")
            assertEquals(2982, sessions[0].totalDescent, "total_descent")
        }

        @Test fun `per-record power accessible`() {
            val withPower = records.filter { (it.power ?: 0) > 0 }
            assertTrue(withPower.isNotEmpty(), "at least one record must carry power > 0")
        }
    }
}
