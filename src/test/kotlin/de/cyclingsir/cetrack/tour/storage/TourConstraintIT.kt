package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.support.MySQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class TourConstraintIT : MySQLContainerIT() {

    @Autowired private lateinit var tourRepository: TourRepository
    @Autowired private lateinit var bikeRepository: BikeRepository

    @Test
    fun `uq_tour_started_distance_duration rejects duplicate tour`() {
        val bike = bikeRepository.saveAndFlush(BikeEntity(
            id = null,
            model = "TestBike",
            manufacturer = "TestMfg"
        ))

        val startedAt = Instant.parse("2024-06-01T10:00:00Z")
        val first = TourEntity(
            id = null,
            mtTourId = "mt-001",
            title = "Tour A",
            distance = 42_000,
            durationMoving = 7200L,
            startedAt = startedAt,
            startYear = 2024,
            startMonth = 6,
            startDay = 1,
            altUp = 100,
            altDown = 100,
            powerTotal = 0L,
            bike = bike,
        )
        tourRepository.saveAndFlush(first)

        val duplicate = TourEntity(
            id = null,
            mtTourId = "mt-002",
            title = "Tour B (duplicate key)",
            distance = 42_000,
            durationMoving = 7200L,
            startedAt = startedAt,
            startYear = 2024,
            startMonth = 6,
            startDay = 1,
            altUp = 200,
            altDown = 200,
            powerTotal = 0L,
            bike = bike,
        )

        val ex = assertThrows<DataIntegrityViolationException> {
            tourRepository.saveAndFlush(duplicate)
        }
        assertThat(ex.message).isNotNull()
        assertThat(ex.message!!).containsIgnoringCase("uq_tour_started_distance_duration")
    }
}
