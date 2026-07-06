package de.cyclingsir.cetrack.maintenance.domain

import java.time.Duration
import java.time.Instant

/**
 * Pure due-derivation (domain-model.md §5.4, CE-0088 plan §4) - no Spring, no
 * persistence - so every branch is unit-testable without a database.
 *
 * Baseline when no event exists is the bike's full tour history: the OR/gate
 * formula below naturally yields due=false when there are neither events nor
 * tours (distanceSinceLast=0, timeSinceLast=null), with no special case needed.
 */
object DueCalculator {

    fun calculate(
        distanceInterval: Long?,
        timeInterval: Long?,
        lastPerformedAt: Instant?,
        distanceSinceLast: Long,
        firstTourAt: Instant?,
        now: Instant,
    ): DomainMaintenanceDue {
        val timeBaseline = lastPerformedAt ?: firstTourAt
        val timeSinceLast = timeBaseline?.let { Duration.between(it, now).seconds }

        val distanceDue = distanceInterval != null && distanceSinceLast >= distanceInterval
        val timeDue = timeInterval != null && timeSinceLast != null && timeSinceLast >= timeInterval

        return DomainMaintenanceDue(
            due = distanceDue || timeDue,
            lastPerformedAt = lastPerformedAt,
            distanceSinceLast = distanceSinceLast,
            distanceRemaining = distanceInterval?.let { it - distanceSinceLast },
            timeSinceLast = timeSinceLast,
            timeRemaining = if (timeInterval != null && timeSinceLast != null) timeInterval - timeSinceLast else null
        )
    }
}
