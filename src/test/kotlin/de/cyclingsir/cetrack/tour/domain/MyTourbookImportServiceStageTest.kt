package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.derby.DerbyReadAdapter
import de.cyclingsir.cetrack.tour.storage.ImportSessionEntity
import de.cyclingsir.cetrack.tour.domain.DomainImportWarning
import de.cyclingsir.cetrack.tour.storage.ImportSessionRepository
import de.cyclingsir.cetrack.tour.storage.ImportStateEntity
import de.cyclingsir.cetrack.tour.storage.ImportStateRepository
import de.cyclingsir.cetrack.tour.storage.ImportIgnoreRepository
import de.cyclingsir.cetrack.tour.storage.TourRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class MyTourbookImportServiceStageTest {

    @MockK private lateinit var tourRepository: TourRepository
    @MockK private lateinit var bikeRepository: BikeRepository
    @MockK private lateinit var sessionRepository: ImportSessionRepository
    @MockK private lateinit var stateRepository: ImportStateRepository
    @MockK private lateinit var ignoreRepository: ImportIgnoreRepository
    @MockK private lateinit var derbyAdapter: DerbyReadAdapter
    @MockK private lateinit var archiveExtractor: ArchiveExtractor

    private val objectMapper = ObjectMapper()
    private lateinit var service: MyTourbookImportService

    companion object {
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
        val BIKE_B: UUID = UUID.fromString("b2222222-0002-0002-0002-000000000002")
        private val DUMMY_TOURBOOK = Path.of("/tmp/dummy-tourbook")
        private const val DB_VERSION = 59
        private const val STATUS_PENDING = "PENDING"
    }

    @BeforeEach
    fun setup() {
        service = MyTourbookImportService(
            tourRepository, bikeRepository, sessionRepository, stateRepository,
            ignoreRepository, derbyAdapter, archiveExtractor, objectMapper, workDir = "/tmp/cetrack-test"
        )
        every { archiveExtractor.extract(any(), any()) } returns DUMMY_TOURBOOK
        every { bikeRepository.findAll() } returns listOf(
            BikeEntity(id = BIKE_A, model = "Bike A"),
            BikeEntity(id = BIKE_B, model = "Bike B")
        )
        every { stateRepository.findById(1) } returns Optional.of(
            ImportStateEntity(1, DB_VERSION, Instant.now())
        )
        every { tourRepository.existsByMtTourId(any()) } returns false
        every { ignoreRepository.existsByStartedAtAndDistanceAndDurationMoving(any(), any(), any()) } returns false
        every { tourRepository.findAllByStartedAtAndDistanceAndDurationMoving(any(), any(), any()) } returns emptyList()
        every { sessionRepository.findAllByStatus(STATUS_PENDING) } returns emptyList()
        every { sessionRepository.save(any<ImportSessionEntity>()) } answers { firstArg() }
        every { sessionRepository.saveAll(any<List<ImportSessionEntity>>()) } answers { firstArg() }
    }

    private fun aMTTour(mtTourId: String = "9000000000001", bikeId: UUID? = BIKE_A) = DomainMTTour(
        MTTOURID = mtTourId,
        TITLE = "Test Tour $mtTourId",
        DISTANCE = 30_000,
        DURATIONMOVING = 5400L,
        STARTTIMESTAMP = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli(),
        STARTYEAR = 2026,
        STARTMONTH = 1,
        STARTDAY = 15,
        TOURALTUP = 200,
        TOURALTDOWN = 200,
        POWERTOTAL = 0L,
        bikeId = bikeId
    )

    private fun emptyStream() = ByteArrayInputStream(ByteArray(0))

    // #21
    @Test
    fun `stage classifies clean rows as candidates`() {
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(aMTTour("9000000000001"), aMTTour("9000000000002"))
        )

        val session = service.stage(emptyStream())!!

        assertEquals(2, session.candidates.size)
        assertTrue(session.warnings.isEmpty())
        assertEquals(STATUS_PENDING, session.status)
    }

    // #22
    @Test
    fun `stage filters already-imported tours instead of rejecting the batch`() {
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(
                aMTTour("9000000000001"),
                aMTTour("9000000000002"),
                aMTTour("9000000000003")
            )
        )
        every { tourRepository.existsByMtTourId("9000000000002") } returns true

        val session = service.stage(emptyStream())!!

        assertEquals(2, session.candidates.size, "already-imported tour must be filtered, others kept")
        assertTrue(session.candidates.none { it.MTTOURID == "9000000000002" })
    }

    // #23
    @Test
    fun `stage signals hasDrift when DBVERSION differs from baseline`() {
        every { stateRepository.findById(1) } returns Optional.of(
            ImportStateEntity(1, lastDbVersion = DB_VERSION - 1, updatedAt = Instant.now())
        )
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(aMTTour())
        )

        val session = service.stage(emptyStream())!!

        assertTrue(session.hasDrift, "hasDrift must be true when dbVersion differs from baseline")
    }

    // #24
    @Test
    fun `stage signals no drift when DBVERSION matches baseline`() {
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(aMTTour())
        )

        val session = service.stage(emptyStream())!!

        assertFalse(session.hasDrift, "hasDrift must be false when dbVersion matches baseline")
    }

    // #25 + #26
    @Test
    fun `new stage marks existing PENDING session SUPERSEDED ensuring single-PENDING invariant`() {
        val priorSession = ImportSessionEntity(UUID.randomUUID(), STATUS_PENDING, DB_VERSION, "[]")
        every { sessionRepository.findAllByStatus(STATUS_PENDING) } returns listOf(priorSession)
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(aMTTour())
        )

        service.stage(emptyStream())

        assertEquals("SUPERSEDED", priorSession.status, "prior PENDING session must be marked SUPERSEDED")
        verify { sessionRepository.saveAll(match<List<ImportSessionEntity>> { it.contains(priorSession) }) }
        verify { sessionRepository.save(match<ImportSessionEntity> { it.status == STATUS_PENDING }) }
    }

    // #45
    @Test
    fun `re-staging supersedes prior session without erasing its candidates and detects version drift`() {
        val priorPayload = """[{"MTTOURID":"9000000000001"}]"""
        val priorSession = ImportSessionEntity(UUID.randomUUID(), STATUS_PENDING, DB_VERSION, priorPayload)
        val newDbVersion = DB_VERSION + 1

        every { sessionRepository.findAllByStatus(STATUS_PENDING) } returns listOf(priorSession)
        every { stateRepository.findById(1) } returns Optional.of(
            ImportStateEntity(1, lastDbVersion = DB_VERSION, updatedAt = Instant.now())
        )
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            newDbVersion, listOf(aMTTour("9000000000002"))
        )

        val newSession = service.stage(emptyStream())!!

        assertEquals("SUPERSEDED", priorSession.status)
        assertEquals(priorPayload, priorSession.payload, "supersede must not erase prior session payload")
        assertEquals(STATUS_PENDING, newSession.status)
        assertEquals(newDbVersion, newSession.dbVersion)
        assertTrue(newSession.hasDrift, "dbVersion changed from baseline must signal hasDrift")
        assertEquals(1, newSession.candidates.size)
        assertEquals("9000000000002", newSession.candidates[0].MTTOURID)
    }

    // Slice-1 regression: warnings must survive the payload round-trip and be returned by getPendingSession
    @Test
    fun `getPendingSession surfaces LOGICAL_DUPLICATE warnings from persisted session payload`() {
        val sessionId = UUID.randomUUID()
        val candidate = aMTTour("9000000000001")
        val warning = DomainImportWarning(
            "LOGICAL_DUPLICATE", "9000000000099",
            "Tour 9000000000099 matches an existing tour by start time, distance, and moving duration — possible re-import from device"
        )
        val payload = objectMapper.writeValueAsString(
            mapOf("candidates" to listOf(candidate), "warnings" to listOf(warning))
        )
        every { sessionRepository.findFirstByStatus(STATUS_PENDING) } returns
            ImportSessionEntity(sessionId, STATUS_PENDING, DB_VERSION, payload)

        val session = service.getPendingSession()

        assertEquals(1, session?.warnings?.size, "warnings must be returned from persisted payload")
        assertEquals("LOGICAL_DUPLICATE", session?.warnings?.first()?.type)
        assertEquals("9000000000099", session?.warnings?.first()?.mtTourId)
        assertEquals(1, session?.candidates?.size)
    }

    // #28
    @Test
    fun `stage returns null when archive yields no new candidates and no warnings`() {
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(aMTTour("9000000000001"))
        )
        every { tourRepository.existsByMtTourId("9000000000001") } returns true

        val result = service.stage(emptyStream())

        assertNull(result)
        verify(exactly = 0) { sessionRepository.save(any()) }
        verify(exactly = 0) { sessionRepository.saveAll(any<List<ImportSessionEntity>>()) }
    }

    // #29
    @Test
    fun `stage returns PENDING session when zero candidates but warnings exist`() {
        every { derbyAdapter.read(any(), any()) } returns DerbyReadAdapter.ReadResult(
            DB_VERSION, listOf(aMTTour("9000000000001", BIKE_A), aMTTour("9000000000001", BIKE_B))
        )

        val result = service.stage(emptyStream())!!

        assertNotNull(result)
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.warnings.size)
        assertEquals("AMBIGUOUS_BIKE", result.warnings[0].type)
    }

    // #27
    @Test
    fun `Derby failure leaves no import_session row`() {
        every { derbyAdapter.read(any(), any()) } throws
            ServiceException(ErrorCodesDomain.DERBY_SCHEMA_INCOMPATIBLE, "schema error")

        assertThrows<ServiceException> { service.stage(emptyStream()) }

        verify(exactly = 0) { sessionRepository.save(any()) }
        verify(exactly = 0) { sessionRepository.saveAll(any<List<ImportSessionEntity>>()) }
    }
}
