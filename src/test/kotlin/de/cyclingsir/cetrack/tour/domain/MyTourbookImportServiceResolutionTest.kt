package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.configuration.MyTourbookImportConfiguration
import de.cyclingsir.cetrack.tour.derby.DerbyReadAdapter
import de.cyclingsir.cetrack.tour.storage.ImportIgnoreEntity
import de.cyclingsir.cetrack.tour.storage.ImportIgnoreRepository
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
class MyTourbookImportServiceResolutionTest {

    @MockK private lateinit var tourRepository: TourRepository
    @MockK private lateinit var bikeRepository: BikeRepository
    @MockK private lateinit var sessionRepository: ImportSessionRepository
    @MockK private lateinit var stateRepository: ImportStateRepository
    @MockK private lateinit var ignoreRepository: ImportIgnoreRepository
    @MockK private lateinit var derbyAdapter: DerbyReadAdapter
    @MockK private lateinit var archiveExtractor: ArchiveExtractor
    @MockK private lateinit var mockedConfig: MyTourbookImportConfiguration

    private val objectMapper = ObjectMapper()
    private lateinit var service: MyTourbookImportService

    companion object {
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
        val BIKE_B: UUID = UUID.fromString("b2222222-0002-0002-0002-000000000002")
        val EXISTING_TOUR_ID: UUID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001")
        private const val DB_VERSION = 59
        private val SESSION_ID: UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000001")
        private val STARTED_AT = Instant.parse("2026-01-15T08:00:00Z")
        private const val MT_TOUR_ID = "9000000000001"
    }

    @BeforeEach
    fun setup() {
        service = MyTourbookImportService(
            tourRepository, bikeRepository, sessionRepository, stateRepository,
            ignoreRepository, derbyAdapter, archiveExtractor, objectMapper, mockedConfig
        )
        every { mockedConfig.workdir } returns "/tmp/cetrack-test"
        every { tourRepository.existsByMtTourIdAndSourceNot(any(), TourSource.FIT) } returns false
        every { tourRepository.saveAll(any<List<TourEntity>>()) } returns emptyList()
        every { tourRepository.save(any<TourEntity>()) } answers { firstArg() }
        every { bikeRepository.findById(any<UUID>()) } returns Optional.empty()
        every { ignoreRepository.save(any<ImportIgnoreEntity>()) } answers { firstArg() }
        every { ignoreRepository.existsByStartedAtAndDistanceBetween(any(), any(), any()) } returns false
        every { stateRepository.findById(1) } returns Optional.of(
            ImportStateEntity(1, DB_VERSION - 1, Instant.now())
        )
        every { stateRepository.save(any<ImportStateEntity>()) } answers { firstArg() }
        every { sessionRepository.save(any<ImportSessionEntity>()) } answers { firstArg() }
    }

    private fun aMTTour(mtTourId: String = MT_TOUR_ID, bikeId: UUID? = BIKE_A) = DomainMTTour(
        MTTOURID = mtTourId,
        TITLE = "Tour $mtTourId",
        DISTANCE = 30_000,
        DURATIONMOVING = 5400L,
        STARTTIMESTAMP = STARTED_AT.toEpochMilli(),
        STARTYEAR = 2026,
        STARTMONTH = 1,
        STARTDAY = 15,
        TOURALTUP = 200,
        TOURALTDOWN = 150,
        POWERTOTAL = 12_000L,
        bikeId = bikeId
    )

    private fun anExistingTour(bikeId: UUID? = BIKE_A) = TourEntity(
        id = EXISTING_TOUR_ID,
        mtTourId = "8000000000001",
        title = "Old Title",
        distance = 30_000,
        durationMoving = 5400L,
        startedAt = STARTED_AT,
        startYear = 2026.toShort(),
        startMonth = 1.toShort(),
        startDay = 15.toShort(),
        ascent = 100,
        descent = 80,
        powerTotal = 0L,
        bike = bikeId?.let { BikeEntity(id = it, model = "Bike") }
    )

    private fun sessionWithWarning(
        incoming: DomainMTTour = aMTTour(),
        matchedTours: List<DomainTourSummary> = listOf(
            DomainTourSummary(EXISTING_TOUR_ID, "Old Title", STARTED_AT, 30_000, 5400L, BIKE_A)
        ),
        replaceDisabled: Boolean = false
    ): ImportSessionEntity {
        val warning = DomainImportWarning(
            type = "LOGICAL_DUPLICATE",
            mtTourId = incoming.MTTOURID,
            message = "duplicate",
            incomingCandidate = incoming,
            matchedTours = matchedTours,
            replaceDisabled = replaceDisabled
        )
        val payload = objectMapper.writeValueAsString(
            mapOf("candidates" to emptyList<Any>(), "warnings" to listOf(warning))
        )
        return ImportSessionEntity(SESSION_ID, "PENDING", DB_VERSION, payload)
    }

    // #35 — updatedAt stamping moved to TourEntity's @PreUpdate (ImportConstraintIT
    // proves it on PG); a mocked repository never fires JPA callbacks
    @Test
    fun `REPLACE overwrites matched tour fields`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(sessionWithWarning())
        every { tourRepository.findById(EXISTING_TOUR_ID) } returns Optional.of(anExistingTour())
        val saved = slot<TourEntity>()
        every { tourRepository.save(capture(saved)) } answers { firstArg() }

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "REPLACE")))

        val updated = saved.captured
        assertEquals(MT_TOUR_ID, updated.mtTourId)
        assertEquals("Tour $MT_TOUR_ID", updated.title)
        assertEquals(30_000, updated.distance)
        assertEquals(5400L, updated.durationMoving)
        assertNull(updated.createdAt, "createdAt must remain null (not set by Replace)")
    }

    // #36
    @Test
    fun `REPLACE rejected with 400 when replaceDisabled is true`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            sessionWithWarning(replaceDisabled = true)
        )

        val ex = assertThrows<ServiceException> {
            service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "REPLACE")))
        }
        assertEquals(ErrorCodesDomain.IMPORT_RESOLUTION_REPLACE_AMBIGUOUS, ex.getError())
        assertEquals(400, (ex.getError() as ErrorCodesDomain).httpStatus)
    }

    // #37
    @Test
    fun `IMPORT_NEW saves new tour and creates import_ignore row when bikes differ`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            sessionWithWarning(
                incoming = aMTTour(bikeId = BIKE_B),
                matchedTours = listOf(DomainTourSummary(EXISTING_TOUR_ID, "Old", STARTED_AT, 30_000, 5400L, BIKE_A))
            )
        )
        val savedTour = slot<TourEntity>()
        every { tourRepository.save(capture(savedTour)) } answers { firstArg() }
        val savedIgnore = slot<ImportIgnoreEntity>()
        every { ignoreRepository.save(capture(savedIgnore)) } answers { firstArg() }

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "IMPORT_NEW")))

        assertEquals(MT_TOUR_ID, savedTour.captured.mtTourId)
        assertEquals(STARTED_AT, savedIgnore.captured.startedAt)
        assertEquals(30_000, savedIgnore.captured.distance)
        assertEquals(5400L, savedIgnore.captured.durationMoving)
    }

    // #38
    @Test
    fun `IMPORT_NEW rejected with 400 when incoming bike matches matched tour bike`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            sessionWithWarning(
                incoming = aMTTour(bikeId = BIKE_A),
                matchedTours = listOf(DomainTourSummary(EXISTING_TOUR_ID, "Old", STARTED_AT, 30_000, 5400L, BIKE_A))
            )
        )

        val ex = assertThrows<ServiceException> {
            service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "IMPORT_NEW")))
        }
        assertEquals(ErrorCodesDomain.IMPORT_RESOLUTION_SAME_BIKE, ex.getError())
        assertEquals(400, (ex.getError() as ErrorCodesDomain).httpStatus)
    }

    // #39
    @Test
    fun `SUPPRESS creates import_ignore row for matched tour triple`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(sessionWithWarning())
        val savedIgnore = slot<ImportIgnoreEntity>()
        every { ignoreRepository.save(capture(savedIgnore)) } answers { firstArg() }

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "SUPPRESS")))

        assertEquals(STARTED_AT, savedIgnore.captured.startedAt)
        assertEquals(30_000, savedIgnore.captured.distance)
        assertEquals(5400L, savedIgnore.captured.durationMoving)
    }

    // #40
    @Test
    fun `SUPPRESS is idempotent when triple already in ignore set`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(sessionWithWarning())
        every { ignoreRepository.existsByStartedAtAndDistanceBetween(any(), any(), any()) } returns true

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "SUPPRESS")))

        verify(exactly = 0) { ignoreRepository.save(any()) }
    }

    // #41
    @Test
    fun `unknown mtTourId in warningResolutions is silently ignored`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(sessionWithWarning())

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest("NONEXISTENT", "SUPPRESS")))

        verify(exactly = 0) { ignoreRepository.save(any()) }
        verify(exactly = 0) { tourRepository.save(any()) }
    }

    // CE-0069: REPLACE of a FIT-sourced row must flip source → MYTOURBOOK
    @Test
    fun `REPLACE flips source to MYTOURBOOK when overwriting a FIT-sourced row`() {
        val fitRow = anExistingTour(bikeId = BIKE_A).also { it.source = TourSource.FIT }
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(sessionWithWarning())
        every { tourRepository.findById(EXISTING_TOUR_ID) } returns Optional.of(fitRow)
        val saved = slot<TourEntity>()
        every { tourRepository.save(capture(saved)) } answers { firstArg() }

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "REPLACE")))

        assertEquals(TourSource.MYTOURBOOK, saved.captured.source, "REPLACE must flip source to MYTOURBOOK")
    }

    // CE-0069: mapToEntity must stamp MYTOURBOOK on plain candidates committed via IMPORT_NEW
    @Test
    fun `IMPORT_NEW saves tour with source MYTOURBOOK`() {
        every { sessionRepository.findById(SESSION_ID) } returns Optional.of(
            sessionWithWarning(
                incoming = aMTTour(bikeId = BIKE_B),
                matchedTours = listOf(DomainTourSummary(EXISTING_TOUR_ID, "Old", STARTED_AT, 30_000, 5400L, BIKE_A))
            )
        )
        val savedTour = slot<TourEntity>()
        every { tourRepository.save(capture(savedTour)) } answers { firstArg() }
        every { ignoreRepository.save(any<ImportIgnoreEntity>()) } answers { firstArg() }

        service.commit(SESSION_ID, emptyList(), listOf(WarningResolutionRequest(MT_TOUR_ID, "IMPORT_NEW")))

        assertEquals(TourSource.MYTOURBOOK, savedTour.captured.source, "IMPORT_NEW must stamp source = MYTOURBOOK")
    }
}
