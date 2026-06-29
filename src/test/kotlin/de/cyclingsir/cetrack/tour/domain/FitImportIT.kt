package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.tour.storage.TourRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class FitImportIT {

    @Autowired private lateinit var fitImportService: FitImportService
    @Autowired private lateinit var tourService: TourService
    @Autowired private lateinit var tourRepository: TourRepository

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
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/Bye_bye_Silverton.fit"))

        assertEquals(1, drafts.size)
        val draft = drafts[0].draft
        assertEquals(2138, draft.distance)
        assertEquals(573L, draft.durationRecorded)
        assertEquals(627L, draft.durationElapsed)
        assertTrue(draft.durationMoving <= draft.durationRecorded,
            "durationMoving must be <= durationRecorded")
        // Silverton file is Strava/Suunto-origin: totalAscent/Descent absent in session message → 0
        assertEquals(0, draft.altUp)
        assertEquals(0, draft.altDown)
        assertEquals(0L, draft.powerTotal)       // no power sensor in Silverton file
        assertEquals(TourSource.FIT, draft.source)
        assertTrue(drafts[0].existingMatches.isEmpty(), "no duplicates expected on first import")
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
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/Bye_bye_Silverton.fit"))
        val draft = drafts[0].draft

        val bike = de.cyclingsir.cetrack.bike.domain.DomainBike(
            model = "Road Bike", manufacturer = null, id = BIKE_A, boughtAt = null, retiredAt = null, createdAt = null
        )
        val created = tourService.addTour(draft.copy(title = "Silverton Ride", bike = bike), TourSource.FIT)

        assertNotNull(created.id)
        assertNotNull(created.mtTourId, "genMtId must be populated")
        assertEquals(TourSource.FIT, created.source)
        assertEquals("Silverton Ride", created.title)

        val entity = tourRepository.findById(created.id!!).get()
        assertEquals(TourSource.FIT, entity.source)
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `re-import same FIT file with same bike throws TOUR_DUPLICATE`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/Bye_bye_Silverton.fit"))
        val draft = drafts[0].draft
        val bike = de.cyclingsir.cetrack.bike.domain.DomainBike(
            model = "Road Bike", manufacturer = null, id = BIKE_A, boughtAt = null, retiredAt = null, createdAt = null
        )

        // First create succeeds
        tourService.addTour(draft.copy(title = "Silverton Ride", bike = bike), TourSource.FIT)

        // Second create with same bike → 409
        val ex = assertThrows(ServiceException::class.java) {
            tourService.addTour(draft.copy(title = "Silverton Ride 2", bike = bike), TourSource.FIT)
        }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
    }

    @Test
    @Sql(statements = [
        "INSERT INTO bike (id, model) VALUES ('a1111111-0001-0001-0001-000000000001', 'Road Bike')"
    ])
    fun `re-import same FIT file with same bike surfaces duplicateHint on parse`() {
        val drafts = fitImportService.parseToDrafts(loadFit("fit-fixture/Bye_bye_Silverton.fit"))
        val draft = drafts[0].draft
        val bike = de.cyclingsir.cetrack.bike.domain.DomainBike(
            model = "Road Bike", manufacturer = null, id = BIKE_A, boughtAt = null, retiredAt = null, createdAt = null
        )

        // Persist it first
        tourService.addTour(draft.copy(title = "Silverton Ride", bike = bike), TourSource.FIT)

        // Parse again → duplicateHint should be populated
        val drafts2 = fitImportService.parseToDrafts(loadFit("fit-fixture/Bye_bye_Silverton.fit"))
        assertTrue(drafts2[0].existingMatches.isNotEmpty(), "existing match should be surfaced")
        assertEquals(1, drafts2[0].existingMatches.size)
    }
}
