package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.tour.storage.TourRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class TracerBulletIT {

    @Autowired private lateinit var importService: MyTourbookImportService
    @Autowired private lateinit var tourRepository: TourRepository

    companion object {
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
        const val FIRST_BIKE_A_TOUR = "9000000000001"
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Bike A')",
        "INSERT INTO bike (id, model) VALUES ('b2222222-0002-0002-0002-000000000002', 'Bike B')",
        "INSERT INTO import_state (id, last_db_version, updated_at) VALUES (1, 59, CURRENT_TIMESTAMP)"
    ])
    fun `stage one candidate then get session then commit persists the tour`() {
        val fixture = javaClass.classLoader.getResourceAsStream("mytourbook-fixture/tourbook.tar.bz2")!!

        // Stage
        val session = importService.stage(fixture)!!
        assertNotNull(session.sessionId)
        assertEquals("PENDING", session.status)
        assertTrue(session.candidates.isNotEmpty(), "expected candidates from fixture")
        assertTrue(session.candidates.any { it.MTTOURID == FIRST_BIKE_A_TOUR },
            "first bikeA tour must be a candidate")

        // Get session
        val retrieved = importService.getSession(session.sessionId)
        assertEquals(session.sessionId, retrieved.sessionId)
        assertEquals("PENDING", retrieved.status)
        assertEquals(session.candidates.size, retrieved.candidates.size)

        // Commit one tour
        importService.commit(session.sessionId, listOf(FIRST_BIKE_A_TOUR))

        // Verify tour was persisted with correct fields
        val tours = tourRepository.findAll()
        assertEquals(1, tours.size, "exactly one tour should be committed")
        val persisted = tours.first()
        assertEquals(FIRST_BIKE_A_TOUR, persisted.mtTourId)
        assertEquals(BIKE_A, persisted.bike?.id, "tour must be linked to bike A")
    }
}
