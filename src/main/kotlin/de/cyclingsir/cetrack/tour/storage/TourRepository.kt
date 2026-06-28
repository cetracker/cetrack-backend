package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
@Repository
interface TourRepository : JpaRepository<TourEntity, UUID> {
    fun existsByStartedAtAndDistanceAndDurationMoving(startedAt: Instant, distance: Int, durationMoving: Long): Boolean
    fun existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
        startedAt: Instant, distance: Int, durationRecorded: Long, durationElapsed: Long, bike: BikeEntity?
    ): Boolean
    fun existsByMtTourId(mtTourId: String): Boolean
    fun findAllByStartedAtAndDistanceAndDurationMoving(startedAt: Instant, distance: Int, durationMoving: Long): List<TourEntity>

    @Modifying
    @Query("UPDATE tour t SET t.durationRecorded = :recorded, t.durationElapsed = :elapsed WHERE t.mtTourId = :mtTourId")
    fun updateDeviceTimes(@Param("mtTourId") mtTourId: String, @Param("recorded") recorded: Long, @Param("elapsed") elapsed: Long): Int
}
