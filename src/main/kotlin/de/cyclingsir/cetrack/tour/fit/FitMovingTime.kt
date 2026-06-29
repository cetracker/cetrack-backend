package de.cyclingsir.cetrack.tour.fit

/**
 * Computes durationMoving from raw FIT records per the CE-0067 algorithm.
 *
 * Mirrors MT's break-loop structure (BREAK_TIME_METHOD_BY_AVG_SLICE_SPEED defaults) using
 * device/raw speed instead of Jamet-smoothed series. Does not bit-match MT's stored value.
 *
 * @param records per-session record series, ordered by time
 * @param timerTime FIT total_timer_time in seconds; returned as-is when no distance series
 */
fun computeDurationMoving(records: List<FitRecord>, timerTime: Long): Long {
    if (records.size < 2) return timerTime

    val hasDistance = records.any { it.distanceM != null && it.distanceM > 0f }
    if (!hasDistance) return timerTime

    var breakSec = 0L
    for (i in 1 until records.size) {
        val prev = records[i - 1]
        val curr = records[i]
        val dt = curr.timeSec - prev.timeSec
        if (dt <= 0) continue

        val sliceSpeed: Float? = if (prev.distanceM != null && curr.distanceM != null) {
            (curr.distanceM - prev.distanceM) * 3.6f / dt
        } else null

        // Fall back to sliceSpeed when device speed is absent (CE-0067 edge case)
        val avgSpeed: Float? = curr.speedMps?.times(3.6f) ?: sliceSpeed

        val isBreak = dt >= 2 &&
            (avgSpeed == null || avgSpeed < 1.0f || (sliceSpeed != null && sliceSpeed < 1.0f))
        if (isBreak) breakSec += dt
    }

    val totalElapsed = records.last().timeSec - records.first().timeSec
    return maxOf(0L, totalElapsed - breakSec)
}
