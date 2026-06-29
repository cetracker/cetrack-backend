package de.cyclingsir.cetrack.tour.fit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FitTourParserTest {

    @Test
    fun `bucketRecordsBySession - single session assigns all records within window`() {
        val records = listOf(
            FitRecord(timeSec = 100L, distanceM = 0f, speedMps = 5f),
            FitRecord(timeSec = 110L, distanceM = 50f, speedMps = 5f),
            FitRecord(timeSec = 200L, distanceM = 500f, speedMps = 5f)
        )
        // window: [100_000ms, 210_000ms]
        val windows = listOf(100_000L to 210_000L)
        val result = bucketRecordsBySession(records, windows)
        assertEquals(1, result.size)
        assertEquals(3, result[0].size)
    }

    @Test
    fun `bucketRecordsBySession - two sessions split records correctly`() {
        val records = listOf(
            FitRecord(timeSec = 100L, distanceM = 0f, speedMps = 5f),
            FitRecord(timeSec = 110L, distanceM = 50f, speedMps = 5f),
            FitRecord(timeSec = 500L, distanceM = 0f, speedMps = 5f),
            FitRecord(timeSec = 510L, distanceM = 50f, speedMps = 5f),
            FitRecord(timeSec = 520L, distanceM = 100f, speedMps = 5f)
        )
        // session 1: [100_000, 200_000]; session 2: [500_000, 600_000]
        val windows = listOf(100_000L to 200_000L, 500_000L to 600_000L)
        val result = bucketRecordsBySession(records, windows)
        assertEquals(2, result.size)
        assertEquals(2, result[0].size, "session 1 should have 2 records")
        assertEquals(3, result[1].size, "session 2 should have 3 records")
    }

    @Test
    fun `bucketRecordsBySession - records outside all windows are dropped`() {
        val records = listOf(
            FitRecord(timeSec = 50L, distanceM = 0f, speedMps = 5f),   // before window
            FitRecord(timeSec = 100L, distanceM = 10f, speedMps = 5f), // in window
            FitRecord(timeSec = 300L, distanceM = 500f, speedMps = 5f) // after window
        )
        val windows = listOf(100_000L to 200_000L)
        val result = bucketRecordsBySession(records, windows)
        assertEquals(1, result[0].size)
        assertEquals(100L, result[0][0].timeSec)
    }

    @Test
    fun `bucketRecordsBySession - record assigned to first matching session only`() {
        // Overlapping windows (edge case: record at boundary of both)
        val records = listOf(
            FitRecord(timeSec = 200L, distanceM = 100f, speedMps = 5f)
        )
        val windows = listOf(100_000L to 200_000L, 200_000L to 300_000L)
        val result = bucketRecordsBySession(records, windows)
        assertEquals(1, result[0].size, "assigned to first matching session")
        assertEquals(0, result[1].size, "not duplicated into second session")
    }

    @Test
    fun `bucketRecordsBySession - empty records yields empty buckets`() {
        val windows = listOf(100_000L to 200_000L, 300_000L to 400_000L)
        val result = bucketRecordsBySession(emptyList(), windows)
        assertEquals(2, result.size)
        assertTrue(result.all { it.isEmpty() })
    }

    private fun assertTrue(condition: Boolean, message: String = "") {
        if (!condition) throw AssertionError(message)
    }
}
