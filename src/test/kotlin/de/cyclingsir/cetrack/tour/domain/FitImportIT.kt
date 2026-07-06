package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Transactional
class FitImportIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var fitImportService: FitImportService
    @Autowired private lateinit var tourService: TourService
    @Autowired private lateinit var tourRepository: TourRepository
    @Autowired private lateinit var bikeRepository: BikeRepository

    companion object {
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
    }

    private fun loadFit(resource: String) =
        FitImportIT::class.java.classLoader.getResourceAsStream(resource)!!

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `parse real FIT file returns one draft with expected summary fields`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))

        assertEquals(1, drafts.size)
        val draft = drafts[0].draft
        assertEquals(18986, draft.distance)
        assertEquals(5689L, draft.durationRecorded)
        assertEquals(10255L, draft.durationElapsed)
        assertTrue(draft.durationMoving <= draft.durationRecorded,
            "durationMoving must be <= durationRecorded")
        // ELEMNT ROAM file has elevation data from device
        assertEquals(91, draft.ascent)
        assertEquals(79, draft.descent)
        assertEquals(344410L, draft.powerTotal)
        assertEquals(TourSource.FIT, draft.source)
        assertTrue(drafts[0].existingMatches.isEmpty(), "no duplicates expected on first import")
    }

    @Test
    fun `parse ELEMNT ROAM FIT file with 38 session fields returns one draft`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2025-06-07-060955-ELEMNT-ROAM.fit"))
        assertEquals(1, drafts.size)
        assertEquals(TourSource.FIT, drafts[0].draft.source)
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `parse power FIT file maps powerTotal from total_work`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2022-08-24-134314.fit"))

        assertEquals(1, drafts.size)
        assertEquals(1_762_974L, drafts[0].draft.powerTotal)
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `create tour from FIT draft persists with source=FIT and genMtId`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))
        val draft = drafts[0].draft

        val bike = de.cyclingsir.cetrack.bike.domain.DomainBike(
            model = "Road Bike", manufacturer = null, id = BIKE_A, retiredAt = null, createdAt = null
        )
        val created = tourService.addTour(draft.copy(title = "ELEMNT ROAM Ride", bike = bike), TourSource.FIT)

        assertNotNull(created.id)
        assertNotNull(created.mtTourId, "genMtId must be populated")
        assertEquals(TourSource.FIT, created.source)
        assertEquals("ELEMNT ROAM Ride", created.title)

        val entity = tourRepository.findById(created.id!!).get()
        assertEquals(TourSource.FIT, entity.source)
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `re-import same FIT file with same bike throws TOUR_DUPLICATE`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))
        val draft = drafts[0].draft
        val bike = de.cyclingsir.cetrack.bike.domain.DomainBike(
            model = "Road Bike", manufacturer = null, id = BIKE_A, retiredAt = null, createdAt = null
        )

        // First create succeeds
        tourService.addTour(draft.copy(title = "ELEMNT ROAM Ride", bike = bike), TourSource.FIT)

        // Second create with same bike → 409
        val ex = assertThrows(ServiceException::class.java) {
            tourService.addTour(draft.copy(title = "ELEMNT ROAM Ride 2", bike = bike), TourSource.FIT)
        }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `re-import same FIT file with same bike surfaces duplicateHint on parse`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))
        val draft = drafts[0].draft
        val bike = de.cyclingsir.cetrack.bike.domain.DomainBike(
            model = "Road Bike", manufacturer = null, id = BIKE_A, retiredAt = null, createdAt = null
        )

        // Persist it first
        tourService.addTour(draft.copy(title = "ELEMNT ROAM Ride", bike = bike), TourSource.FIT)

        // Parse again → duplicateHint should be populated
        val drafts2 = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))
        assertTrue(drafts2[0].existingMatches.isNotEmpty(), "existing match should be surfaced")
        assertEquals(1, drafts2[0].existingMatches.size)
    }

    // CE-0072: FIT hint uses distance tolerance — a tour with same startedAt but slightly different distance is surfaced
    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `parseToDrafts surfaces duplicate hint when existing tour has same startedAt and distance within tolerance`() {
        // Parse to discover the real startedAt and distance from the FIT file
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))
        assertEquals(1, drafts.size)
        val draft = drafts[0].draft
        // distance = 18986; tol = maxOf(round(18986 * 0.005), 5) = 95; range = [18891, 19081]
        // seed a tour 50 m off — still within tolerance
        val nearbyDistance = draft.distance + 50
        val seededAt = draft.startedAt
        tourRepository.save(TourEntity(
            id = null,
            mtTourId = "FIT-dedup-test-72",
            title = "Nearby Ride",
            distance = nearbyDistance,
            durationMoving = draft.durationMoving,
            startedAt = seededAt,
            startYear = seededAt.atZone(ZoneOffset.UTC).year.toShort(),
            startMonth = seededAt.atZone(ZoneOffset.UTC).monthValue.toShort(),
            startDay = seededAt.atZone(ZoneOffset.UTC).dayOfMonth.toShort(),
            ascent = draft.ascent,
            descent = draft.descent,
            powerTotal = draft.powerTotal,
            bike = bikeRepository.getReferenceById(BIKE_A)
        ))

        // Re-parse → tolerance match must surface as hint
        val drafts2 = fitImportService.parseToDrafts(loadFit("fit-fixture/2024-09-04-071701-ELEMNT_ROAM.fit"))
        assertTrue(drafts2[0].existingMatches.isNotEmpty(),
            "tolerance match must be surfaced as duplicateHint (distance off by $nearbyDistance - ${draft.distance} = 50 m)")
    }
}
