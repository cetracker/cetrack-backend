package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.derby.DerbyReadAdapter
import de.cyclingsir.cetrack.tour.storage.ImportSessionEntity
import de.cyclingsir.cetrack.tour.storage.ImportSessionRepository
import de.cyclingsir.cetrack.tour.storage.ImportStateEntity
import de.cyclingsir.cetrack.tour.storage.ImportStateRepository
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Optional
import java.util.UUID


@ExtendWith(MockKExtension::class)
class MyTourbookImportServiceCommitTest {

    @MockK private lateinit var tourRepository: TourRepository
    @MockK private lateinit var bikeRepository: BikeRepository
    @MockK private lateinit var sessionRepository: ImportSessionRepository
    @MockK private lateinit var stateRepository: ImportStateRepository
    @MockK private lateinit var derbyAdapter: DerbyReadAdapter
    @MockK private lateinit var archiveExtractor: ArchiveExtractor

    private val objectMapper = ObjectMapper()
    private lateinit var service: MyTourbookImportService

    companion object {
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
        private const val DB_VERSION = 59
        private val SESSION_ID: UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000001")
        private const val STATUS_PENDING = "PENDING"
    }

    @BeforeEach
    fun setup() {
        service = MyTourbookImportService(
            tourRepository, bikeRepository, sessionRepository, stateRepository,
            derbyAdapter, archiveExtractor, objectMapper, workDir = "/tmp/cetrack-test"
        )
        every { tourRepository.existsByMtTourId(any()) } returns false
        every { tourRepository.saveAll(any<List<TourEntity>>()) } returns emptyList()
        every { bikeRepository.findById(any<UUID>()) } returns Optional.empty()
        every { stateRepository.findById(1) } returns Optional.of(
            ImportStateEntity(1, DB_VERSION - 1, Instant.now())
        )
        every { stateRepository.save(any<ImportStateEntity>()) } answers { firstArg() }
        every { sessionRepository.save(any<ImportSessionEntity>()) } answers { firstArg() }
    }

    private fun aMTTour(
        mtTourId: String = "9000000000001",
        bikeId: UUID? = BIKE_A,
        timestamp: Long = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli()
    ) = DomainMTTour(
        MTTOURID = mtTourId,
        TITLE = "Tour $mtTourId",
        DISTANCE = 30_000,
        DURATIONMOVING = 5400L,
        STARTTIMESTAMP = timestamp,
        STARTYEAR = 2026,
        STARTMONTH = 1,
        STARTDAY = 15,
        TOURALTUP = 200,
        TOURALTDOWN = 150,
        POWERTOTAL = 12_000L,
        bikeId = bikeId
    )

    private fun pendingSession(vararg tours: DomainMTTour): ImportSessionEntity {
        val payload = objectMapper.writeValueAsString(tours.toList())
        return ImportSessionEntity(SESSION_ID, STATUS_PENDING, DB_VERSION, payload)
    }

    // #28
    @Test
    fun `commit persists only approvedMtTourIds intersected with session candidates`() {
        val tourA = aMTTour("9000000000001")
        val tourB = aMTTour("9000000000002")
        val tourC = aMTTour("9000000000003")
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            pendingSession(tourA, tourB, tourC)
        )
        val saved = slot<List<TourEntity>>()
        every { tourRepository.saveAll(capture(saved)) } returns emptyList()

        service.commit(SESSION_ID, listOf("9000000000001", "9000000000003"))

        val savedIds = saved.captured.mapNotNull { it.mtTourId }
        assertEquals(listOf("9000000000001", "9000000000003"), savedIds.sorted())
        assertFalse(savedIds.contains("9000000000002"), "non-approved tour must not be persisted")
    }

    // #29
    @Test
    fun `commit re-checks existsByMtTourId and skips a row imported since stage`() {
        val tourA = aMTTour("9000000000001")
        val tourB = aMTTour("9000000000002")
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            pendingSession(tourA, tourB)
        )
        every { tourRepository.existsByMtTourId("9000000000002") } returns true
        val saved = slot<List<TourEntity>>()
        every { tourRepository.saveAll(capture(saved)) } returns emptyList()

        service.commit(SESSION_ID, listOf("9000000000001", "9000000000002"))

        assertEquals(1, saved.captured.size, "race-imported tour must be skipped")
        assertEquals("9000000000001", saved.captured.first().mtTourId)
    }

    // #30
    @Test
    fun `commit advances import_state baseline to session db_version`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            pendingSession(aMTTour())
        )
        val savedState = slot<ImportStateEntity>()
        every { stateRepository.save(capture(savedState)) } answers { firstArg() }

        service.commit(SESSION_ID, listOf("9000000000001"))

        assertEquals(DB_VERSION, savedState.captured.lastDbVersion,
            "import_state baseline must be advanced to session dbVersion")
    }

    // #31
    @Test
    fun `commit on a SUPERSEDED session throws IMPORT_SESSION_SUPERSEDED`() {
        val superseded = ImportSessionEntity(SESSION_ID, "SUPERSEDED", DB_VERSION, "[]")
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(superseded)

        val ex = assertThrows<ServiceException> {
            service.commit(SESSION_ID, listOf("9000000000001"))
        }
        assertEquals(ErrorCodesDomain.IMPORT_SESSION_SUPERSEDED, ex.getError())
    }

    // #32
    @Test
    fun `commit on a COMMITTED session throws IMPORT_SESSION_SUPERSEDED`() {
        val committed = ImportSessionEntity(SESSION_ID, "COMMITTED", DB_VERSION, "[]")
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(committed)

        val ex = assertThrows<ServiceException> {
            service.commit(SESSION_ID, listOf("9000000000001"))
        }
        assertEquals(ErrorCodesDomain.IMPORT_SESSION_SUPERSEDED, ex.getError())
    }

    // #33
    @Test
    fun `commit on an unknown session id throws IMPORT_SESSION_NOT_FOUND`() {
        every { sessionRepository.findById(any()) } returns Optional.empty()

        val ex = assertThrows<ServiceException> {
            service.commit(SESSION_ID, listOf("9000000000001"))
        }
        assertEquals(ErrorCodesDomain.IMPORT_SESSION_NOT_FOUND, ex.getError())
    }

    // #34
    @Test
    fun `committed tour fields match the expected mapping from DomainMTTour`() {
        val timestamp = Instant.parse("2026-03-10T09:30:00Z").toEpochMilli()
        val tour = aMTTour("9000000000001", bikeId = BIKE_A, timestamp = timestamp)
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(pendingSession(tour))
        every { bikeRepository.findById(BIKE_A) } returns Optional.of(
            BikeEntity(id = BIKE_A, model = "Bike A")
        )
        val saved = slot<List<TourEntity>>()
        every { tourRepository.saveAll(capture(saved)) } returns emptyList()

        service.commit(SESSION_ID, listOf("9000000000001"))

        val persisted = saved.captured.first()
        assertNull(persisted.id, "new tour must have null id (generated by DB)")
        assertEquals("9000000000001", persisted.mtTourId)
        assertEquals("Tour 9000000000001", persisted.title)
        assertEquals(30_000, persisted.distance)
        assertEquals(5400L, persisted.durationMoving)
        assertEquals(Instant.ofEpochMilli(timestamp), persisted.startedAt)
        assertEquals(2026, persisted.startYear.toInt())
        assertEquals(1, persisted.startMonth.toInt())
        assertEquals(15, persisted.startDay.toInt())
        assertEquals(200, persisted.altUp)
        assertEquals(150, persisted.altDown)
        assertEquals(12_000L, persisted.powerTotal)
        assertEquals(BIKE_A, persisted.bike?.id, "tour must link to the correct bike")
    }
}
