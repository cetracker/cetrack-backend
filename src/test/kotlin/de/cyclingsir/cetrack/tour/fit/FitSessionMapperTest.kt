package de.cyclingsir.cetrack.tour.fit

import com.garmin.fit.DateTime
import com.garmin.fit.SessionMesg
import de.cyclingsir.cetrack.tour.domain.TourSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class FitSessionMapperTest {

    private val mapper = FitSessionMapper()

    // Bye_bye_Silverton reference: start=1658086511000 ms, distance=2137.94m, timer=573s, elapsed=627s
    private fun silvertonSession(): SessionMesg {
        val s = SessionMesg()
        s.startTime = DateTime(java.util.Date(1_658_086_511_000L))
        s.totalDistance = 2137.94f
        s.totalTimerTime = 573f
        s.totalElapsedTime = 627f
        s.totalAscent = 33
        s.totalDescent = 35
        return s
    }

    @Test
    fun `maps start time to UTC instant`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assertEquals(Instant.ofEpochMilli(1_658_086_511_000L), draft.startedAt)
        val utc = draft.startedAt.atOffset(ZoneOffset.UTC)
        assertEquals(2022.toShort(), draft.startYear)
        assertEquals(7.toShort(), draft.startMonth)
        assertEquals(17.toShort(), draft.startDay)
    }

    @Test
    fun `maps distance rounded to int`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assertEquals(2138, draft.distance) // 2137.94 rounded
    }

    @Test
    fun `maps device times`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assertEquals(573L, draft.durationRecorded)
        assertEquals(627L, draft.durationElapsed)
    }

    @Test
    fun `maps elevation, null becomes 0`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assertEquals(33, draft.altUp)
        assertEquals(35, draft.altDown)
    }

    @Test
    fun `elevation null fields default to 0`() {
        val s = silvertonSession()
        s.totalAscent = null
        s.totalDescent = null
        val draft = mapper.map(s, emptyList())
        assertEquals(0, draft.altUp)
        assertEquals(0, draft.altDown)
    }

    @Test
    fun `title is empty string, bike is null`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assertEquals("", draft.title)
        assertNull(draft.bike)
    }

    @Test
    fun `source is FIT`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assertEquals(TourSource.FIT, draft.source)
    }

    @Test
    fun `powerTotal - total_work present`() {
        val s = silvertonSession()
        s.totalWork = 1_762_974L
        val draft = mapper.map(s, emptyList())
        assertEquals(1_762_974L, draft.powerTotal)
    }

    @Test
    fun `powerTotal - fallback to avgPower times timerTime`() {
        val s = silvertonSession()
        s.totalWork = null
        s.avgPower = 228
        // 228 * 573 = 130_644
        val draft = mapper.map(s, emptyList())
        assertEquals(228L * 573L, draft.powerTotal)
    }

    @Test
    fun `powerTotal - both null gives 0`() {
        val s = silvertonSession()
        s.totalWork = null
        s.avgPower = null
        val draft = mapper.map(s, emptyList())
        assertEquals(0L, draft.powerTotal)
    }

    @Test
    fun `durationMoving le durationRecorded (invariant)`() {
        val draft = mapper.map(silvertonSession(), emptyList())
        assert(draft.durationMoving <= draft.durationRecorded) {
            "durationMoving=${draft.durationMoving} must be <= durationRecorded=${draft.durationRecorded}"
        }
    }

    @Test
    fun `multi-session - two sessions produce two independent drafts`() {
        val s1 = silvertonSession()
        val s2 = silvertonSession().also {
            it.startTime = DateTime(java.util.Date(1_661_348_594_000L))
            it.totalDistance = 76597.8f
            it.totalTimerTime = 10541f
            it.totalElapsedTime = 13930f
            it.totalWork = 1_762_974L
        }
        val d1 = mapper.map(s1, emptyList())
        val d2 = mapper.map(s2, emptyList())
        assertEquals(2138, d1.distance)
        assertEquals(76598, d2.distance)
        assertEquals(573L, d1.durationRecorded)
        assertEquals(10541L, d2.durationRecorded)
    }
}
