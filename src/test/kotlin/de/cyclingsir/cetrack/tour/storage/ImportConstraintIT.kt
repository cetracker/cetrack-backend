package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class ImportConstraintIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var tourRepository: TourRepository
    @Autowired private lateinit var bikeRepository: BikeRepository
    @Autowired private lateinit var ignoreRepository: ImportIgnoreRepository

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

    // #42
    @Test
    fun `new tour has null updatedAt but non-null after explicit stamp`() {
        val saved = tourRepository.saveAndFlush(aTourEntity("9000000000010", "New Tour"))
        assertThat(saved.updatedAt).isNull()

        saved.updatedAt = Instant.now()
        val updated = tourRepository.saveAndFlush(saved)
        assertThat(updated.updatedAt).isNotNull()
    }

    // #43
    @Test
    @org.junit.jupiter.api.Disabled("CE-0084: V1.0 baseline has no import_ignore triple unique - import dedupe semantics re-verified there")
    fun `import_ignore insert succeeds and triple unique constraint rejects duplicate`() {
        val triple = ImportIgnoreEntity(
            startedAt = Instant.parse("2026-03-01T07:00:00Z"),
            distance = 50_000,
            durationMoving = 7200L
        )
        ignoreRepository.saveAndFlush(triple)

        val duplicate = ImportIgnoreEntity(
            startedAt = Instant.parse("2026-03-01T07:00:00Z"),
            distance = 50_000,
            durationMoving = 7200L
        )
        val ex = assertThrows<DataIntegrityViolationException> {
            ignoreRepository.saveAndFlush(duplicate)
        }
        assertThat(ex.message).isNotNull()
        assertThat(ex.message!!).containsIgnoringCase("uq_import_ignore_triple")
    }
}
