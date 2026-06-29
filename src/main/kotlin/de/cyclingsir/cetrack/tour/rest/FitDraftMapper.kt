package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.infrastructure.api.model.ExistingTourSummary
import de.cyclingsir.cetrack.infrastructure.api.model.FitDraftTour
import de.cyclingsir.cetrack.infrastructure.api.model.FitDuplicateHint
import de.cyclingsir.cetrack.tour.domain.FitImportService.DraftWithHint
import de.cyclingsir.cetrack.tour.storage.TourEntity
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class FitDraftMapper {

    fun map(draftWithHint: DraftWithHint): FitDraftTour {
        val draft = draftWithHint.draft
        val startedAt = draft.startedAt.atOffset(ZoneOffset.UTC)
        return FitDraftTour(
            distance = draft.distance,
            durationMoving = draft.durationMoving,
            durationRecorded = draft.durationRecorded,
            durationElapsed = draft.durationElapsed,
            altUp = draft.altUp,
            altDown = draft.altDown,
            powerTotal = draft.powerTotal,
            startedAt = startedAt,
            startYear = draft.startYear,
            startMonth = draft.startMonth,
            startDay = draft.startDay,
            title = draft.title.ifEmpty { null },
            bike = null,
            duplicateHint = draftWithHint.existingMatches.takeIf { it.isNotEmpty() }
                ?.let { FitDuplicateHint(matchedTours = it.map(::toSummary)) }
        )
    }

    private fun toSummary(entity: TourEntity): ExistingTourSummary = ExistingTourSummary(
        tourId = entity.id!!,
        title = entity.title,
        startedAt = entity.startedAt.atOffset(ZoneOffset.UTC),
        distance = entity.distance,
        durationMoving = entity.durationMoving,
        bikeId = entity.bike?.id
    )
}
