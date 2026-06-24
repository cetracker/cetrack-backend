package de.cyclingsir.cetrack.tour.domain

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.derby.DerbyReadAdapter
import de.cyclingsir.cetrack.tour.storage.ImportSessionEntity
import de.cyclingsir.cetrack.tour.storage.ImportSessionRepository
import de.cyclingsir.cetrack.tour.storage.ImportStateEntity
import de.cyclingsir.cetrack.tour.storage.ImportStateRepository
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class MyTourbookImportService(
    private val tourRepository: TourRepository,
    private val bikeRepository: BikeRepository,
    private val sessionRepository: ImportSessionRepository,
    private val stateRepository: ImportStateRepository,
    private val derbyAdapter: DerbyReadAdapter,
    private val archiveExtractor: ArchiveExtractor,
    private val objectMapper: ObjectMapper,
    @Value("\${app.mytourbook.workdir}") private val workDir: String
) {

    @Transactional
    fun stage(inputStream: InputStream): DomainImportSession {
        val sessionId = UUID.randomUUID()
        val base = Path.of(workDir).also {
            runCatching { Files.createDirectories(it) }
                .onFailure { e -> logger.error(e) { "Failed to create or access work directory '$workDir'" } }
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
            val result = derbyAdapter.read(tourBookDir, bikeUuids)
            val (candidates, warnings) = classifyRows(result.rows)
            val mtDeduped = candidates.filter { !tourRepository.existsByMtTourId(it.MTTOURID) }
            val (newCandidates, logicalDupWarnings) = filterLogicalDuplicates(mtDeduped)
            val allWarnings = warnings + logicalDupWarnings
            val hasDrift = result.dbVersion != state.lastDbVersion
            val payload = objectMapper.writeValueAsString(newCandidates)
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
        val candidates: List<DomainMTTour> = objectMapper.readValue(
            entity.payload, object : TypeReference<List<DomainMTTour>>() {}
        )
        val state = stateRepository.findById(IMPORT_STATE_ID)
            .orElseGet { ImportStateEntity(IMPORT_STATE_ID, 0, Instant.now()) }
        val hasDrift = entity.dbVersion != state.lastDbVersion
        return DomainImportSession(entity.id, entity.status, entity.dbVersion, hasDrift, candidates, emptyList())
    }

    @Transactional
    fun commit(sessionId: UUID, approvedMtTourIds: List<String>) {
        val entity = sessionRepository.findById(sessionId)
            .orElseThrow { ServiceException(ErrorCodesDomain.IMPORT_SESSION_NOT_FOUND, "Session $sessionId not found") }
        if (entity.status != "PENDING") {
            throw ServiceException(ErrorCodesDomain.IMPORT_SESSION_SUPERSEDED, "Session $sessionId is ${entity.status}")
        }
        val candidates: List<DomainMTTour> = objectMapper.readValue(
            entity.payload, object : TypeReference<List<DomainMTTour>>() {}
        )
        val approved = candidates.filter { it.MTTOURID in approvedMtTourIds }
        val toImport = approved.filter { !tourRepository.existsByMtTourId(it.MTTOURID) }
        tourRepository.saveAll(toImport.map { mapToEntity(it) })
        val state = stateRepository.findById(IMPORT_STATE_ID)
            .orElseGet { ImportStateEntity(IMPORT_STATE_ID, 0, Instant.now()) }
        state.lastDbVersion = entity.dbVersion
        state.updatedAt = Instant.now()
        stateRepository.save(state)
        entity.status = "COMMITTED"
        sessionRepository.save(entity)
    }

    // False-positive risk: two rides with identical start time + distance + moving duration would both be skipped.
    // Accepted: CETracker is single-user and a bike can only be on one tour at a time.
    private fun filterLogicalDuplicates(candidates: List<DomainMTTour>): Pair<List<DomainMTTour>, List<DomainImportWarning>> {
        val result = mutableListOf<DomainMTTour>()
        val warnings = mutableListOf<DomainImportWarning>()
        for (candidate in candidates) {
            val startedAt = Instant.ofEpochMilli(candidate.STARTTIMESTAMP)
            if (tourRepository.existsByStartedAtAndDistanceAndDurationMoving(startedAt, candidate.DISTANCE, candidate.DURATIONMOVING)) {
                warnings += DomainImportWarning(
                    "LOGICAL_DUPLICATE", candidate.MTTOURID,
                    "Tour ${candidate.MTTOURID} matches an existing tour by start time, distance, and moving duration — possible re-import from device"
                )
            } else {
                result += candidate
            }
        }
        return result to warnings
    }

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
            startedAt = startedAt,
            startYear = mtTour.STARTYEAR,
            startMonth = mtTour.STARTMONTH,
            startDay = mtTour.STARTDAY,
            altUp = mtTour.TOURALTUP,
            altDown = mtTour.TOURALTDOWN,
            powerTotal = mtTour.POWERTOTAL,
            bike = bikeEntity
        )
    }

    companion object {
        private const val IMPORT_STATE_ID = 1
    }
}
