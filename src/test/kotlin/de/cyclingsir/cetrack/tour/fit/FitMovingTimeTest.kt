package de.cyclingsir.cetrack.tour.fit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FitMovingTimeTest {

    private fun records(vararg points: Triple<Long, Float?, Float?>): List<FitRecord> =
        points.map { (t, d, s) -> FitRecord(timeSec = t, distanceM = d, speedMps = s) }

    @Test
    fun `normal ride - moving time excludes low-speed stop`() {
        // t=0..100 moving at 5 m/s, t=100..130 stopped (0 speed, no distance delta)
        val r = records(
            Triple(0L, 0f, 5f),
            Triple(10L, 50f, 5f),
            Triple(20L, 100f, 5f),
            Triple(30L, 150f, 5f),
            Triple(40L, 200f, 5f),
            Triple(50L, 250f, 5f),
            Triple(60L, 300f, 5f),
            Triple(70L, 350f, 5f),
            Triple(80L, 400f, 5f),
            Triple(90L, 450f, 5f),
            Triple(100L, 500f, 5f),
            // 30 s stop: speed=0, no distance change
            Triple(130L, 500f, 0f),
            Triple(140L, 550f, 5f),
            Triple(150L, 600f, 5f)
        )
        // total elapsed = 150, break = 30 s stop, moving = 120
        val result = computeDurationMoving(r, timerTime = 120L)
        assertEquals(120L, result)
    }

    @Test
    fun `auto-pause gap - large delta-t with near-zero slice speed counts as break`() {
        // 0-100: riding; then 100-200 gap (auto-pause: no records); sliceSpeed near 0
        val r = records(
            Triple(0L, 0f, 5f),
            Triple(100L, 500f, 5f),
            // auto-pause: next record jumps 200 s later, no distance change
            Triple(300L, 500f, 0f),
            Triple(310L, 550f, 5f),
            Triple(320L, 600f, 5f)
        )
        // elapsed = 320, break = 200 s slice (sliceSpeed = 0/200 ≈ 0 km/h, Δt=200 >= 2)
        val result = computeDurationMoving(r, timerTime = 120L)
        assertEquals(120L, result)
    }

    @Test
    fun `missing record speed - falls back to sliceSpeed`() {
        val r = records(
            Triple(0L, 0f, null),
            Triple(10L, 50f, null),   // sliceSpeed = 50/10 * 3.6 = 18 km/h → not a break
            Triple(20L, 100f, null),
            Triple(50L, 100f, null),  // sliceSpeed = 0/30 * 3.6 = 0 km/h, Δt=30 → break
            Triple(60L, 150f, null)
        )
        val result = computeDurationMoving(r, timerTime = 30L)
        // elapsed=60, break=30, moving=30
        assertEquals(30L, result)
    }

    @Test
    fun `no distance series - returns timerTime`() {
        val r = records(
            Triple(0L, null, 5f),
            Triple(10L, null, 5f),
            Triple(20L, null, 5f)
        )
        val result = computeDurationMoving(r, timerTime = 573L)
        assertEquals(573L, result)
    }

    @Test
    fun `empty records - returns timerTime`() {
        assertEquals(573L, computeDurationMoving(emptyList(), timerTime = 573L))
    }

    @Test
    fun `single record - returns timerTime`() {
        val r = records(Triple(0L, 0f, 5f))
        assertEquals(573L, computeDurationMoving(r, timerTime = 573L))
    }

    @Test
    fun `result never negative`() {
        // pathological: all records are stopped
        val r = records(
            Triple(0L, 0f, 0f),
            Triple(10L, 0f, 0f),
            Triple(20L, 0.1f, 0f)
        )
        val result = computeDurationMoving(r, timerTime = 0L)
        assertTrue(result >= 0)
    }

    @Test
    fun `moving le recorded le elapsed invariant holds after clamping`() {
        val r = records(
            Triple(0L, 0f, 5f),
            Triple(10L, 50f, 5f),
            Triple(20L, 100f, 5f)
        )
        val moving = computeDurationMoving(r, timerTime = 20L)
        val recorded = 20L
        val elapsed = 25L
        assertTrue(moving <= recorded, "moving=$moving must be <= recorded=$recorded")
        assertTrue(recorded <= elapsed, "recorded=$recorded must be <= elapsed=$elapsed")
    }
}
