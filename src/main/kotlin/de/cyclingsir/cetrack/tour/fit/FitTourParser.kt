package de.cyclingsir.cetrack.tour.fit

import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.io.InputStream

private val logger = KotlinLogging.logger {}

class FitParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class FitParsedSession(
    val session: SessionMesg,
    val records: List<FitRecord>
)

@Component
class FitTourParser {

    fun parse(inputStream: InputStream): List<FitParsedSession> {
        val sessions = mutableListOf<SessionMesg>()
        val rawRecords = mutableListOf<RecordMesg>()

        try {
            val broadcaster = MesgBroadcaster(Decode())
            broadcaster.addListener(SessionMesgListener { sessions.add(it) })
            broadcaster.addListener(RecordMesgListener { rawRecords.add(it) })
            inputStream.use { broadcaster.run(it) }
        } catch (e: Exception) {
            throw FitParseException("Failed to parse FIT file: ${e.message}", e)
        }

        if (sessions.isEmpty()) throw FitParseException("FIT file contains no sessions")

        val fitRecords = rawRecords.mapNotNull { r ->
            val ts = r.timestamp ?: return@mapNotNull null
            FitRecord(
                timeSec = ts.date.time / 1000L,
                distanceM = r.distance,
                speedMps = r.enhancedSpeed ?: r.speed
            )
        }

        val windows = sessions.mapNotNull { s ->
            val start = s.startTime?.date?.time ?: return@mapNotNull null
            val elapsed = s.totalElapsedTime ?: 0f
            val end = start + (elapsed * 1000L).toLong()
            start to end
        }

        val bucketed = bucketRecordsBySession(fitRecords, windows)

        return sessions.mapIndexed { i, session ->
            FitParsedSession(session, bucketed.getOrElse(i) { emptyList() })
        }
    }
}

/**
 * Pure function — separately unit-tested (FitTourParserTest).
 *
 * Assigns each FitRecord to the first session window that contains its timestamp.
 * Records outside all windows are dropped (e.g. a trailing summary record).
 */
fun bucketRecordsBySession(
    records: List<FitRecord>,
    sessionWindows: List<Pair<Long, Long>>
): List<List<FitRecord>> {
    val buckets = List(sessionWindows.size) { mutableListOf<FitRecord>() }
    for (record in records) {
        val tsMs = record.timeSec * 1000L
        for ((i, window) in sessionWindows.withIndex()) {
            if (tsMs >= window.first && tsMs <= window.second) {
                buckets[i].add(record)
                break
            }
        }
    }
    return buckets
}
