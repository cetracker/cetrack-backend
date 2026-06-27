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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * CE-0066 spike: validates that the Garmin FIT SDK (com.garmin:fit:21.205.0) can decode
 * a real device file and expose all fields required by CE-0064/CE-0067/CE-0068.
 *
 * Expected values sourced from Bye_bye_Silverton.json (MyTourbook reference output):
 *   start_time      2022-07-17 (Unix ms 1658086511000)
 *   total_distance  ~2137.94 m (FIT stores in cm → 213794)
 *   total_timer     573 s
 *   total_elapsed   627 s
 *   total_ascent    33 m
 *   total_descent   35 m
 *   power           no sensor → null/0 (asserts field is accessible, not a specific value)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FitParserSpikeTest {

    private val sessions = mutableListOf<SessionMesg>()
    private val records = mutableListOf<RecordMesg>()

    companion object {
        const val FIXTURE = "fit-fixture/Bye_bye_Silverton.fit"
        // 2022-07-17T19:35:11Z in milliseconds (from JSON tourStartTime)
        const val EXPECTED_START_MS = 1_658_086_511_000L
        const val EXPECTED_DISTANCE_M = 2137.94f
        const val EXPECTED_TIMER_S = 573f
        const val EXPECTED_ELAPSED_S = 627f
    }

    @BeforeAll
    fun parseFitFile() {
        val broadcaster = MesgBroadcaster(Decode())
        broadcaster.addListener(SessionMesgListener { sessions.add(it) })
        broadcaster.addListener(RecordMesgListener { records.add(it) })

        FitParserSpikeTest::class.java.classLoader
            .getResourceAsStream(FIXTURE)!!
            .use { broadcaster.run(it) }
    }

    @Test
    fun `file contains exactly one session`() {
        assertEquals(1, sessions.size, "single-session file must yield exactly 1 SessionMesg")
    }

    @Test
    fun `session start_time is correct`() {
        val startTime = sessions[0].startTime
        assertNotNull(startTime, "start_time must be present")
        assertEquals(EXPECTED_START_MS, startTime!!.date.time, "start_time must match reference")
    }

    @Test
    fun `session total_distance is correct`() {
        val dist = sessions[0].totalDistance
        assertNotNull(dist, "total_distance must be present")
        assertEquals(EXPECTED_DISTANCE_M, dist!!, 1.0f, "total_distance must match reference within 1 m")
    }

    @Test
    fun `session total_timer_time is correct`() {
        val timer = sessions[0].totalTimerTime
        assertNotNull(timer, "total_timer_time must be present")
        assertEquals(EXPECTED_TIMER_S, timer!!, 0.5f, "total_timer_time must match reference")
    }

    @Test
    fun `session total_elapsed_time is correct`() {
        val elapsed = sessions[0].totalElapsedTime
        assertNotNull(elapsed, "total_elapsed_time must be present")
        assertEquals(EXPECTED_ELAPSED_S, elapsed!!, 0.5f, "total_elapsed_time must match reference")
    }

    @Test
    fun `session total_ascent and total_descent are accessible (may be null for non-Garmin files)`() {
        val session = sessions[0]
        // Strava/Suunto FIT files omit total_ascent/total_descent from the session message;
        // MyTourbook computes elevation from the altitude series in those cases.
        // CE-0064 must apply a null→0 fallback. Here we only verify the field is readable.
        val ascent = session.totalAscent   // null for this Strava file
        val descent = session.totalDescent // null for this Strava file
        assertTrue(ascent == null || ascent >= 0, "total_ascent when present must be non-negative")
        assertTrue(descent == null || descent >= 0, "total_descent when present must be non-negative")
    }

    @Test
    fun `session power fields are accessible (null when no sensor)`() {
        val session = sessions[0]
        // Garmin SDK returns null when field is absent in the FIT message.
        // This test validates the field can be read; CE-0064 applies null-coalescing.
        // total_work == null, avg_power == null for this file (no power sensor).
        val totalWork = session.totalWork
        val avgPower = session.avgPower
        // At least one of the two must be accessible (not throw); both may be null.
        assertTrue(totalWork == null || totalWork >= 0L)
        assertTrue(avgPower == null || avgPower >= 0)
    }

    @Test
    fun `records expose timestamp and cumulative distance`() {
        assertTrue(records.isNotEmpty(), "FIT file must contain record messages")
        val withTimestamp = records.filter { it.timestamp != null }
        assertTrue(withTimestamp.isNotEmpty(), "at least one record must have a timestamp")
        val withDistance = records.filter { it.distance != null && it.distance!! > 0f }
        assertTrue(withDistance.isNotEmpty(), "at least one record must have distance > 0")
    }

    @Test
    fun `records expose speed`() {
        val withSpeed = records.filter {
            (it.speed != null && it.speed!! > 0f) || (it.enhancedSpeed != null && it.enhancedSpeed!! > 0f)
        }
        assertTrue(withSpeed.isNotEmpty(), "at least one record must have speed > 0")
    }
}
