package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ImportConstraintIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var tourRepository: TourRepository
    @Autowired private lateinit var bikeRepository: BikeRepository

    private fun aBike() = bikeRepository.save(BikeEntity(id = null, model = "Constraint bike"))

    private fun aTourEntity(mtTourId: String?, title: String) = TourEntity(
        id = null,
        mtTourId = mtTourId,
        title = title,
        distance = 30_000,
        durationMoving = 5400L,
        startedAt = Instant.parse("2026-01-15T08:00:00Z"),
        startYear = 2026.toShort(),
        startMonth = 1.toShort(),
        startDay = 15.toShort(),
        ascent = 200,
        descent = 150,
        powerTotal = 0L,
        bike = aBike()
    )

    // #3 — V1.9 dropped UNIQUE(mt_tour_id); two rows with the same id now save without exception
    @Test
    fun `duplicate mt_tour_id is allowed after V1_9 drops the unique constraint`() {
        tourRepository.saveAndFlush(aTourEntity("9000000000001", "First"))

        assertDoesNotThrow {
            tourRepository.saveAndFlush(aTourEntity("9000000000001", "Duplicate — now allowed"))
        }
    }

    // #42 — @PreUpdate stamps on modification only; a fresh import keeps null
    @Test
    fun `new tour has null updatedAt but non-null after an update`() {
        val saved = tourRepository.saveAndFlush(aTourEntity("9000000000010", "New Tour"))
        assertThat(saved.updatedAt).isNull()

        saved.title = "New Tour (renamed)"
        val updated = tourRepository.saveAndFlush(saved)
        assertThat(updated.updatedAt).isNotNull()
    }
}
