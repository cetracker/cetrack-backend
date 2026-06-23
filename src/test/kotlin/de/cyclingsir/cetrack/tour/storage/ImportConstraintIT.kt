package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.support.MySQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class ImportConstraintIT : MySQLContainerIT() {

    @Autowired private lateinit var tourRepository: TourRepository

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
        altUp = 200,
        altDown = 150,
        powerTotal = 0L
    )

    // #3
    @Test
    fun `uq_tour_mt_tour_id rejects duplicate mt_tour_id`() {
        tourRepository.saveAndFlush(aTourEntity("9000000000001", "First"))

        val ex = assertThrows<DataIntegrityViolationException> {
            tourRepository.saveAndFlush(aTourEntity("9000000000001", "Duplicate"))
        }
        assertThat(ex.message).isNotNull()
        assertThat(ex.message!!).containsIgnoringCase("uq_tour_mt_tour_id")
    }
}
