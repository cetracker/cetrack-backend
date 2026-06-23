package de.cyclingsir.cetrack.tour.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class ImportStorageIT {

    @Autowired private lateinit var tourRepository: TourRepository
    @Autowired private lateinit var sessionRepository: ImportSessionRepository
    @Autowired private lateinit var stateRepository: ImportStateRepository

    private fun aTourEntity(mtTourId: String? = "9000000000001") = TourEntity(
        id = null,
        mtTourId = mtTourId,
        title = "Test Tour",
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

    // #1
    @Test
    fun `existsByMtTourId returns true for a persisted mtTourId`() {
        tourRepository.save(aTourEntity("9000000000001"))
        assertTrue(tourRepository.existsByMtTourId("9000000000001"))
    }

    // #2
    @Test
    fun `existsByMtTourId returns false for an absent mtTourId`() {
        assertFalse(tourRepository.existsByMtTourId("does-not-exist"))
    }

    // #5
    @Test
    fun `import_session round-trips PENDING payload as JSON string`() {
        val id = UUID.randomUUID()
        val payload = """[{"MTTOURID":"9000000000001","TITLE":"Test","DISTANCE":30000}]"""
        sessionRepository.save(ImportSessionEntity(id, "PENDING", 59, payload))

        val loaded = sessionRepository.findById(id).orElseThrow()

        assertEquals("PENDING", loaded.status)
        assertEquals(59, loaded.dbVersion)
        assertEquals(payload, loaded.payload, "payload must survive the round-trip unchanged")
        assertNotNull(loaded.createdAt)
    }

    // #6
    @Test
    fun `import_state single drift-baseline row is retrievable by fixed id`() {
        stateRepository.save(ImportStateEntity(1, 59, Instant.now()))

        val state = stateRepository.findById(1).orElseThrow()

        assertEquals(1, state.id)
        assertEquals(59, state.lastDbVersion)
    }
}
