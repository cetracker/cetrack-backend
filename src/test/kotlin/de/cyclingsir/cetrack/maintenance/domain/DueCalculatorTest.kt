package de.cyclingsir.cetrack.maintenance.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** CE-0088 due-derivation (domain-model.md §5.4), pure - no Spring, no database. */
class DueCalculatorTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `distance-only not due below the interval`() {
        val due = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = null,
            lastPerformedAt = null, distanceSinceLast = 799_999L, firstTourAt = now, now = now
        )
        assertThat(due.due).isFalse()
        assertThat(due.distanceRemaining).isEqualTo(1L)
        assertThat(due.timeRemaining).isNull()
    }

    @Test
    fun `distance-only due at exactly the interval`() {
        val due = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = null,
            lastPerformedAt = null, distanceSinceLast = 800_000L, firstTourAt = now, now = now
        )
        assertThat(due.due).isTrue()
        assertThat(due.distanceRemaining).isEqualTo(0L)
    }

    @Test
    fun `distance-only overdue yields negative remaining`() {
        val due = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = null,
            lastPerformedAt = null, distanceSinceLast = 900_000L, firstTourAt = now, now = now
        )
        assertThat(due.due).isTrue()
        assertThat(due.distanceRemaining).isEqualTo(-100_000L)
    }

    @Test
    fun `time-only not due below the interval`() {
        val lastPerformedAt = now.minusSeconds(89 * 86_400L)
        val due = DueCalculator.calculate(
            distanceInterval = null, timeInterval = 90 * 86_400L,
            lastPerformedAt = lastPerformedAt, distanceSinceLast = 0L, firstTourAt = null, now = now
        )
        assertThat(due.due).isFalse()
        assertThat(due.timeSinceLast).isEqualTo(89 * 86_400L)
        assertThat(due.timeRemaining).isEqualTo(86_400L)
    }

    @Test
    fun `time-only due when elapsed time reaches the interval`() {
        val lastPerformedAt = now.minusSeconds(90 * 86_400L)
        val due = DueCalculator.calculate(
            distanceInterval = null, timeInterval = 90 * 86_400L,
            lastPerformedAt = lastPerformedAt, distanceSinceLast = 0L, firstTourAt = null, now = now
        )
        assertThat(due.due).isTrue()
        assertThat(due.timeRemaining).isEqualTo(0L)
    }

    @Test
    fun `both intervals set - either one being due is an OR`() {
        val dueByDistanceOnly = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = 90 * 86_400L,
            lastPerformedAt = now.minusSeconds(86_400L), distanceSinceLast = 900_000L, firstTourAt = null, now = now
        )
        assertThat(dueByDistanceOnly.due).isTrue()

        val dueByTimeOnly = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = 90 * 86_400L,
            lastPerformedAt = now.minusSeconds(91 * 86_400L), distanceSinceLast = 0L, firstTourAt = null, now = now
        )
        assertThat(dueByTimeOnly.due).isTrue()

        val neitherDue = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = 90 * 86_400L,
            lastPerformedAt = now.minusSeconds(86_400L), distanceSinceLast = 0L, firstTourAt = null, now = now
        )
        assertThat(neitherDue.due).isFalse()
    }

    @Test
    fun `no event yet - baseline is the bike's first tour`() {
        val firstTourAt = now.minusSeconds(91 * 86_400L)
        val due = DueCalculator.calculate(
            distanceInterval = null, timeInterval = 90 * 86_400L,
            lastPerformedAt = null, distanceSinceLast = 0L, firstTourAt = firstTourAt, now = now
        )
        assertThat(due.due).isTrue()
        assertThat(due.timeSinceLast).isEqualTo(91 * 86_400L)
    }

    @Test
    fun `no event and no tours - time part not evaluable, distance zero, not due`() {
        val due = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = 90 * 86_400L,
            lastPerformedAt = null, distanceSinceLast = 0L, firstTourAt = null, now = now
        )
        assertThat(due.due).isFalse()
        assertThat(due.timeSinceLast).isNull()
        assertThat(due.timeRemaining).isNull()
    }

    @Test
    fun `remaining is null for the interval that is not set`() {
        val due = DueCalculator.calculate(
            distanceInterval = 800_000L, timeInterval = null,
            lastPerformedAt = null, distanceSinceLast = 0L, firstTourAt = null, now = now
        )
        assertThat(due.distanceRemaining).isNotNull()
        assertThat(due.timeRemaining).isNull()
    }
}
