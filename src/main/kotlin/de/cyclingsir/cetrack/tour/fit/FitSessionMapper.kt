package de.cyclingsir.cetrack.tour.fit

import com.garmin.fit.SessionMesg
import de.cyclingsir.cetrack.tour.domain.DomainTour
import de.cyclingsir.cetrack.tour.domain.TourSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

@Component
class FitSessionMapper {

    fun map(session: SessionMesg, records: List<FitRecord>): DomainTour {
        val startInstant = Instant.ofEpochMilli(session.startTime.date.time)
        val startUtc = startInstant.atOffset(ZoneOffset.UTC)

        val durationRecorded = session.totalTimerTime?.toLong()?.coerceAtLeast(0L) ?: 0L
        val durationElapsed = session.totalElapsedTime?.toLong()?.coerceAtLeast(0L) ?: 0L

        val durationMovingRaw = computeDurationMoving(records, durationRecorded)
        val durationMoving = durationMovingRaw.coerceAtMost(durationRecorded).also {
            if (durationMovingRaw > durationRecorded) {
                logger.warn { "durationMoving $durationMovingRaw > durationRecorded $durationRecorded — clamped" }
            }
        }

        val powerTotal = session.totalWork?.toLong()
            ?: session.avgPower?.let { it.toLong() * durationRecorded }
            ?: 0L

        return DomainTour(
            id = null,
            mtTourId = null,
            title = "",
            distance = session.totalDistance?.roundToInt() ?: 0,
            durationMoving = durationMoving,
            durationRecorded = durationRecorded,
            durationElapsed = durationElapsed,
            ascent = session.totalAscent ?: 0,
            descent = session.totalDescent ?: 0,
            powerTotal = powerTotal,
            bike = null,
            startedAt = startInstant,
            startYear = startUtc.year.toShort(),
            startMonth = startUtc.monthValue.toShort(),
            startDay = startUtc.dayOfMonth.toShort(),
            createdAt = null,
            source = TourSource.FIT
        )
    }
}
