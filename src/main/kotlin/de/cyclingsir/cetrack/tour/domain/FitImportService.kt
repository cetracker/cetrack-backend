package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.tour.fit.FitParsedSession
import de.cyclingsir.cetrack.tour.fit.FitSessionMapper
import de.cyclingsir.cetrack.tour.fit.FitTourParser
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class FitImportService(
    private val parser: FitTourParser,
    private val sessionMapper: FitSessionMapper,
    private val tourRepository: TourRepository
) {

    data class DraftWithHint(
        val draft: DomainTour,
        val existingMatches: List<TourEntity>
    )

    fun parseToDrafts(inputStream: InputStream): List<DraftWithHint> =
        parser.parse(inputStream).map { it.toDraftWithHint() }

    private fun FitParsedSession.toDraftWithHint(): DraftWithHint {
        val draft = sessionMapper.map(session, records)
        val (lo, hi) = TourDedup.distanceRange(draft.distance)
        val matches = tourRepository.findAllByStartedAtAndDistanceBetween(draft.startedAt, lo, hi)
        return DraftWithHint(draft, matches)
    }
}
