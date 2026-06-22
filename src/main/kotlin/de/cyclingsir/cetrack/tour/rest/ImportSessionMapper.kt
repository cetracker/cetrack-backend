package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.infrastructure.api.model.ImportCandidate
import de.cyclingsir.cetrack.infrastructure.api.model.ImportSession
import de.cyclingsir.cetrack.infrastructure.api.model.ImportSessionStatus
import de.cyclingsir.cetrack.infrastructure.api.model.ImportWarning
import de.cyclingsir.cetrack.tour.domain.DomainImportSession
import de.cyclingsir.cetrack.tour.domain.DomainImportWarning
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset

@Component
class ImportSessionMapper {

    fun map(domain: DomainImportSession): ImportSession = ImportSession(
        sessionId = domain.sessionId,
        status = ImportSessionStatus.forValue(domain.status),
        dbVersion = domain.dbVersion,
        hasDrift = domain.hasDrift,
        candidates = domain.candidates.map(::mapCandidate),
        warnings = domain.warnings.map(::mapWarning)
    )

    private fun mapCandidate(t: DomainMTTour): ImportCandidate = ImportCandidate(
        mtTourId = t.MTTOURID,
        title = t.TITLE,
        startedAt = Instant.ofEpochMilli(t.STARTTIMESTAMP).atOffset(ZoneOffset.UTC),
        distance = t.DISTANCE,
        durationMoving = t.DURATIONMOVING,
        altUp = t.TOURALTUP,
        altDown = t.TOURALTDOWN,
        powerTotal = t.POWERTOTAL,
        bikeId = t.bikeId
    )

    private fun mapWarning(w: DomainImportWarning): ImportWarning = ImportWarning(
        type = ImportWarning.Type.forValue(w.type),
        mtTourId = w.mtTourId,
        message = w.message
    )
}
