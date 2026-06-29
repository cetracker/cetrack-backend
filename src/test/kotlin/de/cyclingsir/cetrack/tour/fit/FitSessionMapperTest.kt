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

    // 2024-09-04-071701-ELEMNT_ROAM reference: start=1725434221000 ms, distance=18986.32m, timer=5689s, elapsed=10255s
    private fun elementRoamSession(): SessionMesg {
        val s = SessionMesg()
        s.startTime = DateTime(java.util.Date(1_725_434_221_000L))
        s.totalDistance = 18986.32f
        s.totalTimerTime = 5689f
        s.totalElapsedTime = 10255f
        s.totalAscent = 91
        s.totalDescent = 79
        return s
    }

    @Test
    fun `maps start time to UTC instant`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assertEquals(Instant.ofEpochMilli(1_725_434_221_000L), draft.startedAt)
        val utc = draft.startedAt.atOffset(ZoneOffset.UTC)
        assertEquals(2024.toShort(), draft.startYear)
        assertEquals(9.toShort(), draft.startMonth)
        assertEquals(4.toShort(), draft.startDay)
    }

    @Test
    fun `maps distance rounded to int`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assertEquals(18986, draft.distance) // 18986.32 rounded
    }

    @Test
    fun `maps device times`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assertEquals(5689L, draft.durationRecorded)
        assertEquals(10255L, draft.durationElapsed)
    }

    @Test
    fun `maps elevation, null becomes 0`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assertEquals(91, draft.altUp)
        assertEquals(79, draft.altDown)
    }

    @Test
    fun `elevation null fields default to 0`() {
        val s = elementRoamSession()
        s.totalAscent = null
        s.totalDescent = null
        val draft = mapper.map(s, emptyList())
        assertEquals(0, draft.altUp)
        assertEquals(0, draft.altDown)
    }

    @Test
    fun `title is empty string, bike is null`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assertEquals("", draft.title)
        assertNull(draft.bike)
    }

    @Test
    fun `source is FIT`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assertEquals(TourSource.FIT, draft.source)
    }

    @Test
    fun `powerTotal - total_work present`() {
        val s = elementRoamSession()
        s.totalWork = 1_762_974L
        val draft = mapper.map(s, emptyList())
        assertEquals(1_762_974L, draft.powerTotal)
    }

    @Test
    fun `powerTotal - fallback to avgPower times timerTime`() {
        val s = elementRoamSession()
        s.totalWork = null
        s.avgPower = 228
        // 228 * 5689 = 1_297_092
        val draft = mapper.map(s, emptyList())
        assertEquals(228L * 5689L, draft.powerTotal)
    }

    @Test
    fun `powerTotal - both null gives 0`() {
        val s = elementRoamSession()
        s.totalWork = null
        s.avgPower = null
        val draft = mapper.map(s, emptyList())
        assertEquals(0L, draft.powerTotal)
    }

    @Test
    fun `durationMoving le durationRecorded (invariant)`() {
        val draft = mapper.map(elementRoamSession(), emptyList())
        assert(draft.durationMoving <= draft.durationRecorded) {
            "durationMoving=${draft.durationMoving} must be <= durationRecorded=${draft.durationRecorded}"
        }
    }

    @Test
    fun `multi-session - two sessions produce two independent drafts`() {
        val s1 = elementRoamSession()
        val s2 = elementRoamSession().also {
            it.startTime = DateTime(java.util.Date(1_661_348_594_000L))
            it.totalDistance = 76597.8f
            it.totalTimerTime = 10541f
            it.totalElapsedTime = 13930f
            it.totalWork = 1_762_974L
        }
        val d1 = mapper.map(s1, emptyList())
        val d2 = mapper.map(s2, emptyList())
        assertEquals(18986, d1.distance)
        assertEquals(76598, d2.distance)
        assertEquals(5689L, d1.durationRecorded)
        assertEquals(10541L, d2.durationRecorded)
    }
}
