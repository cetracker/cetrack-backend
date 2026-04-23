package de.cyclingsir.cetrack.tour.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
@Repository
interface TourRepository : JpaRepository<TourEntity, UUID> {
    fun existsByStartedAtAndDistanceAndDurationMoving(startedAt: Instant, distance: Int, durationMoving: Long): Boolean
}
