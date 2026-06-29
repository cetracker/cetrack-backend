package de.cyclingsir.cetrack.tour.domain

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.derby.DerbyReadAdapter
import de.cyclingsir.cetrack.tour.storage.ImportIgnoreEntity
import de.cyclingsir.cetrack.tour.storage.ImportIgnoreRepository
import de.cyclingsir.cetrack.tour.storage.ImportSessionEntity
import de.cyclingsir.cetrack.tour.storage.ImportSessionRepository
import de.cyclingsir.cetrack.tour.storage.ImportStateEntity
import de.cyclingsir.cetrack.tour.storage.ImportStateRepository
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import de.cyclingsir.cetrack.tour.configuration.MyTourbookImportConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.lang.classfile.Attributes.record
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

data class WarningResolutionRequest(val mtTourId: String, val action: String)

private val logger = KotlinLogging.logger {}

private data class SessionPayload(
    val candidates: List<DomainMTTour> = emptyList(),
    val warnings: List<DomainImportWarning> = emptyList()
)

@Service
class MyTourbookImportService(
    private val tourRepository: TourRepository,
    private val bikeRepository: BikeRepository,
    private val sessionRepository: ImportSessionRepository,
    private val stateRepository: ImportStateRepository,
    private val ignoreRepository: ImportIgnoreRepository,
    private val derbyAdapter: DerbyReadAdapter,
    private val archiveExtractor: ArchiveExtractor,
    private val objectMapper: ObjectMapper,
    private val config: MyTourbookImportConfiguration
) {

    @Transactional
    fun stage(inputStream: InputStream): DomainImportSession? {
        val sessionId = UUID.randomUUID()
        val base = Path.of(config.workdir).also {
            runCatching { Files.createDirectories(it) }
                .onFailure { e -> logger.error(e) { "Failed to create or access work directory '${config.workdir}'" } }
                .getOrThrow()
        }
        val tempDir = runCatching { Files.createTempDirectory(base, "mytourbook-$sessionId") }
            .onFailure { e -> logger.error(e) { "Failed to create temp directory under '$base'" } }
            .getOrThrow()

        logger.info { "Staging MyTourbook import session $sessionId in $tempDir" }

        return try {
            val tourBookDir = archiveExtractor.extract(inputStream, tempDir)
            val bikeUuids = bikeRepository.findAll().mapNotNull { it.id?.toString() }
            val state = stateRepository.findById(IMPORT_STATE_ID)
                .orElseGet { ImportStateEntity(IMPORT_STATE_ID, 0, Instant.now()) }
            val result = derbyAdapter.read(tourBookDir, bikeUuids, config.tourPersonId, config.tourTypeIds)
            if (!state.deviceTimeBackfilled) {
                backFillDeviceTimes(result, state)
            }
            val (candidates, warnings) = classifyRows(result.rows)
            val mtDeduped = candidates.filter { !tourRepository.existsByMtTourIdAndSourceNot(it.MTTOURID, TourSource.FIT) }
            val (newCandidates, logicalDupWarnings) = filterLogicalDuplicates(mtDeduped)
            val allWarnings = warnings + logicalDupWarnings
            if (newCandidates.isEmpty() && allWarnings.isEmpty()) {
                logger.info { "Staging session $sessionId yielded no new candidates or warnings — no session created" }
                return null
            }
            val hasDrift = result.dbVersion != state.lastDbVersion
            val payload = objectMapper.writeValueAsString(SessionPayload(newCandidates, allWarnings))
            val pending = sessionRepository.findAllByStatus("PENDING")
            if (pending.isNotEmpty()) {
                pending.forEach { it.status = "SUPERSEDED" }
                sessionRepository.saveAll(pending)
            }
            sessionRepository.save(ImportSessionEntity(sessionId, "PENDING", result.dbVersion, payload))
            DomainImportSession(sessionId, "PENDING", result.dbVersion, hasDrift, newCandidates, allWarnings)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun backFillDeviceTimes(
        result: DerbyReadAdapter.ReadResult,
        state: ImportStateEntity
    ) {
        var updatedCount = 0
        val tourCount = tourRepository.count()
        logger.info { "Backfilling device times for ${tourCount} tours from ${result.allDeviceTimes.size} rows in MyTourbook import" }
        result.allDeviceTimes.forEach { (mtTourId, times) ->
            if (tourRepository.updateDeviceTimes(mtTourId, times.first, times.second) > 0) updatedCount++
        }
        logger.info { "Backfill updated $updatedCount tours (${result.allDeviceTimes.size - updatedCount} Derby rows not in CETrack — expected)" }
        if(updatedCount < tourCount) {
            logger.warn { "Could not backfill ${tourCount - updatedCount} tours" }
            tourRepository.findAllByDurationRecordedAndDurationElapsed(0, 0).forEach {
              if(it.mtTourId !in result.allDeviceTimes.keys) {
                  logger.warn { "Tour ${it.startYear}-${it.startMonth}-${it.startDay} - ${it.title} not found in derby query" }
              }
            }
        }
        state.deviceTimeBackfilled = true
        stateRepository.save(state)
    }

    @Transactional(readOnly = true)
    fun getPendingSession(): DomainImportSession? {
        val entity = sessionRepository.findFirstByStatus("PENDING") ?: return null
        return deserializeSession(entity)
    }

    @Transactional(readOnly = true)
    fun getSession(sessionId: UUID): DomainImportSession {
        val entity = sessionRepository.findById(sessionId)
            .orElseThrow { ServiceException(ErrorCodesDomain.IMPORT_SESSION_NOT_FOUND, "Session $sessionId not found") }
        return deserializeSession(entity)
    }

    private fun deserializeSession(entity: ImportSessionEntity): DomainImportSession {
        val payload = deserializePayload(entity.payload)
        val state = stateRepository.findById(IMPORT_STATE_ID)
            .orElseGet { ImportStateEntity(IMPORT_STATE_ID, 0, Instant.now()) }
        val hasDrift = entity.dbVersion != state.lastDbVersion
        return DomainImportSession(entity.id, entity.status, entity.dbVersion, hasDrift, payload.candidates, payload.warnings)
    }

    private fun deserializePayload(json: String): SessionPayload =
        try {
            objectMapper.readValue(json, SessionPayload::class.java)
        } catch (_: Exception) {
            // stale bare-array format from before the wrapper was introduced; re-stage clears it
            val candidates: List<DomainMTTour> = objectMapper.readValue(json, object : TypeReference<List<DomainMTTour>>() {})
            SessionPayload(candidates)
        }

    @Transactional
    fun commit(sessionId: UUID, approvedMtTourIds: List<String>, warningResolutions: List<WarningResolutionRequest> = emptyList()) {
        val entity = sessionRepository.findById(sessionId)
            .orElseThrow { ServiceException(ErrorCodesDomain.IMPORT_SESSION_NOT_FOUND, "Session $sessionId not found") }
        if (entity.status != "PENDING") {
            throw ServiceException(ErrorCodesDomain.IMPORT_SESSION_SUPERSEDED, "Session $sessionId is ${entity.status}")
        }
        val sessionPayload = deserializePayload(entity.payload)
        val approved = sessionPayload.candidates.filter { it.MTTOURID in approvedMtTourIds }
        val toImport = approved.filter { !tourRepository.existsByMtTourIdAndSourceNot(it.MTTOURID, TourSource.FIT) }
        tourRepository.saveAll(toImport.map { mapToEntity(it) })

        for (resolution in warningResolutions) {
            val warning = sessionPayload.warnings
                .find { it.mtTourId == resolution.mtTourId && it.type == "LOGICAL_DUPLICATE" }
                ?: continue

            when (resolution.action) {
                "REPLACE" -> {
                    if (warning.replaceDisabled) {
                        throw ServiceException(ErrorCodesDomain.IMPORT_RESOLUTION_REPLACE_AMBIGUOUS,
                            "REPLACE not allowed for multi-match warning ${resolution.mtTourId}")
                    }
                    val matchedId = warning.matchedTours.first().tourId
                    val existing = tourRepository.findById(matchedId)
                        .orElseThrow { ServiceException(ErrorCodesDomain.IMPORT_TOUR_NOT_FOUND, "Matched tour $matchedId not found") }
                    val incoming = warning.incomingCandidate!!
                    existing.mtTourId = incoming.MTTOURID
                    existing.title = incoming.TITLE
                    existing.distance = incoming.DISTANCE
                    existing.durationMoving = incoming.DURATIONMOVING
                    existing.durationRecorded = incoming.TIMERECORDEDDEVICE ?: 0L
                    existing.durationElapsed = incoming.TIMEELAPSEDDEVICE ?: 0L
                    existing.startedAt = Instant.ofEpochMilli(incoming.STARTTIMESTAMP)
                    existing.startYear = incoming.STARTYEAR
                    existing.startMonth = incoming.STARTMONTH
                    existing.startDay = incoming.STARTDAY
                    existing.altUp = incoming.TOURALTUP
                    existing.altDown = incoming.TOURALTDOWN
                    existing.powerTotal = incoming.POWERTOTAL
                    existing.bike = incoming.bikeId?.let { bikeRepository.findById(it).orElse(null) }
                    existing.source = TourSource.MYTOURBOOK
                    existing.updatedAt = Instant.now()
                    tourRepository.save(existing)
                }
                "IMPORT_NEW" -> {
                    val incoming = warning.incomingCandidate!!
                    val incomingBikeId = incoming.bikeId
                    val matchedBikeId = warning.matchedTours.firstOrNull()?.bikeId
                    if (incomingBikeId == matchedBikeId) {
                        throw ServiceException(ErrorCodesDomain.IMPORT_RESOLUTION_SAME_BIKE,
                            "IMPORT_NEW not allowed when incoming bike matches matched tour bike for ${resolution.mtTourId}")
                    }
                    tourRepository.save(mapToEntity(incoming))
                    suppressTriple(warning.matchedTours.first())
                }
                "SUPPRESS" -> suppressTriple(warning.matchedTours.first())
            }
        }

        val state = stateRepository.findById(IMPORT_STATE_ID)
            .orElseGet { ImportStateEntity(IMPORT_STATE_ID, 0, Instant.now()) }
        state.lastDbVersion = entity.dbVersion
        state.updatedAt = Instant.now()
        stateRepository.save(state)
        entity.status = "COMMITTED"
        sessionRepository.save(entity)
    }

    private fun suppressTriple(summary: DomainTourSummary) {
        if (!ignoreRepository.existsByStartedAtAndDistanceAndDurationMoving(
                summary.startedAt, summary.distance, summary.durationMoving)) {
            ignoreRepository.save(ImportIgnoreEntity(
                startedAt = summary.startedAt,
                distance = summary.distance,
                durationMoving = summary.durationMoving
            ))
        }
    }

    // False-positive risk: two rides with identical start time + distance + moving duration would both be skipped.
    // Accepted: CETracker is single-user and a bike can only be on one tour at a time.
    private fun filterLogicalDuplicates(candidates: List<DomainMTTour>): Pair<List<DomainMTTour>, List<DomainImportWarning>> {
        val result = mutableListOf<DomainMTTour>()
        val warnings = mutableListOf<DomainImportWarning>()
        for (candidate in candidates) {
            val startedAt = Instant.ofEpochMilli(candidate.STARTTIMESTAMP)
            if (ignoreRepository.existsByStartedAtAndDistanceAndDurationMoving(startedAt, candidate.DISTANCE, candidate.DURATIONMOVING)) {
                continue
            }
            val matched = tourRepository.findAllByStartedAtAndDistanceAndDurationMoving(startedAt, candidate.DISTANCE, candidate.DURATIONMOVING)
            if (matched.isNotEmpty()) {
                warnings += DomainImportWarning(
                    type = "LOGICAL_DUPLICATE",
                    mtTourId = candidate.MTTOURID,
                    message = "Tour ${candidate.MTTOURID} matches an existing tour by start time, distance, and moving duration — possible re-import from device",
                    incomingCandidate = candidate,
                    matchedTours = matched.map { it.toSummary() },
                    replaceDisabled = matched.size > 1
                )
            } else {
                result += candidate
            }
        }
        return result to warnings
    }

    private fun TourEntity.toSummary() = DomainTourSummary(
        tourId = id!!,
        title = title,
        startedAt = startedAt,
        distance = distance,
        durationMoving = durationMoving,
        bikeId = bike?.id
    )

    private fun classifyRows(rows: List<DomainMTTour>): Pair<List<DomainMTTour>, List<DomainImportWarning>> {
        val byTourId = rows.groupBy { it.MTTOURID }
        val candidates = mutableListOf<DomainMTTour>()
        val warnings = mutableListOf<DomainImportWarning>()
        for ((tourId, tourRows) in byTourId) {
            if (tourRows.size > 1) {
                warnings += DomainImportWarning("AMBIGUOUS_BIKE", tourId,
                    "Tour $tourId tagged with ${tourRows.size} tracked bikes — skipped")
            } else {
                candidates += tourRows.first()
            }
        }
        return candidates to warnings
    }

    private fun mapToEntity(mtTour: DomainMTTour): TourEntity {
        val startedAt = Instant.ofEpochMilli(mtTour.STARTTIMESTAMP)
        val bikeEntity = mtTour.bikeId?.let { bikeRepository.findById(it).orElse(null) }
        return TourEntity(
            id = null,
            mtTourId = mtTour.MTTOURID,
            title = mtTour.TITLE,
            distance = mtTour.DISTANCE,
            durationMoving = mtTour.DURATIONMOVING,
            durationRecorded = mtTour.TIMERECORDEDDEVICE ?: 0L,
            durationElapsed = mtTour.TIMEELAPSEDDEVICE ?: 0L,
            startedAt = startedAt,
            startYear = mtTour.STARTYEAR,
            startMonth = mtTour.STARTMONTH,
            startDay = mtTour.STARTDAY,
            altUp = mtTour.TOURALTUP,
            altDown = mtTour.TOURALTDOWN,
            powerTotal = mtTour.POWERTOTAL,
            bike = bikeEntity,
            source = TourSource.MYTOURBOOK
        )
    }

    companion object {
        private const val IMPORT_STATE_ID = 1
    }
}
