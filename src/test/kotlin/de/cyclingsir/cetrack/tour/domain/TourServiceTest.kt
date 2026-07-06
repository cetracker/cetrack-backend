package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.storage.TourDomain2StorageMapper
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.UUID

@ExtendWith(MockKExtension::class)
class TourServiceTest {

    @MockK
    private lateinit var repository: TourRepository

    @MockK
    private lateinit var mapper: TourDomain2StorageMapper

    @MockK
    private lateinit var bikeMapper: BikeDomain2StorageMapper

    @MockK
    private lateinit var bikeService: BikeService

    @MockK
    private lateinit var bikeRepository: BikeRepository

    private lateinit var service: TourService

    private val startedAt = Instant.parse("2024-06-01T08:00:00Z")
    private val distance = 50000
    private val durationMoving = 7200L
    private val durationRecorded = 3600L
    private val durationElapsed = 4000L

    private fun aTour(bikeId: UUID? = null) = DomainTour(
        id = null,
        title = "Morning Ride",
        distance = distance,
        durationMoving = durationMoving,
        durationRecorded = durationRecorded,
        durationElapsed = durationElapsed,
        ascent = 500,
        descent = 500,
        powerTotal = 0L,
        bike = bikeId?.let { DomainBike(id = it) },
        startedAt = startedAt,
        startYear = 2024.toShort(),
        startMonth = 6.toShort(),
        startDay = 1.toShort(),
        createdAt = null
    )

    private fun aMTTour(id: String = "MT-001") = DomainMTTour(
        MTTOURID = id,
        TITLE = "Morning Ride",
        DISTANCE = distance,
        DURATIONMOVING = durationMoving,
        STARTTIMESTAMP = startedAt.toEpochMilli(),
        STARTYEAR = 2024.toShort(),
        STARTMONTH = 6.toShort(),
        STARTDAY = 1.toShort(),
        TOURALTUP = 500,
        TOURALTDOWN = 500,
        POWERTOTAL = 0L,
        bikeId = null
    )

    @BeforeEach
    fun init() {
        MockKAnnotations.init(this)
        service = TourService(repository, mapper, bikeMapper, bikeService, bikeRepository)
    }

    @Test
    fun `addTour throws ServiceException when tour with same device times and bike already exists`() {
        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, null)
        } returns true

        val ex = assertThrows(ServiceException::class.java) { service.addTour(aTour()) }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
    }

    @Test
    fun `addTour saves tour when no duplicate exists`() {
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = aTour().copy(id = entity.id, createdAt = entity.createdAt)

        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, null)
        } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        val result = service.addTour(aTour())
        assertEquals(entity.id, result.id)
        verify(exactly = 1) { repository.save(any()) }
    }

    // Dedup matrix: same ride, different bike → allowed (bikeId differs → different entity → no match)
    @Test
    fun `addTour allows same ride on a different bike`() {
        val bikeId = UUID.randomUUID()
        val bikeEntity = BikeEntity(id = bikeId, model = "Bike A")
        val tour = aTour(bikeId = bikeId)
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            bikeEntity, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = tour.copy(id = entity.id, createdAt = entity.createdAt)

        every { bikeMapper.map(any<DomainBike>()) } returns bikeEntity
        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, bikeEntity)
        } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        service.addTour(tour)
        verify(exactly = 1) { repository.save(any()) }
    }

    // Dedup matrix: legacy tour with recorded=0 discriminated by elapsed
    @Test
    fun `addTour allows tour where only elapsed differs (legacy recorded=0 case)`() {
        val tourWithDifferentElapsed = aTour().copy(durationRecorded = 0L, durationElapsed = 5000L)
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = tourWithDifferentElapsed.copy(id = entity.id, createdAt = entity.createdAt)

        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, 0L, 5000L, null)
        } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        service.addTour(tourWithDifferentElapsed)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `importTours throws ServiceException when batch contains a duplicate`() {
        val duplicate = aMTTour("MT-001")
        val fresh = aMTTour("MT-002").copy(STARTTIMESTAMP = startedAt.plusSeconds(3600).toEpochMilli())

        every {
            repository.existsByStartedAtAndDistanceAndDurationMoving(
                Instant.ofEpochMilli(duplicate.STARTTIMESTAMP), duplicate.DISTANCE, duplicate.DURATIONMOVING)
        } returns true
        every {
            repository.existsByStartedAtAndDistanceAndDurationMoving(
                Instant.ofEpochMilli(fresh.STARTTIMESTAMP), fresh.DISTANCE, fresh.DURATIONMOVING)
        } returns false

        val ex = assertThrows(ServiceException::class.java) { service.importTours(listOf(duplicate, fresh)) }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
        verify(exactly = 0) { repository.saveAll(any<List<TourEntity>>()) }
    }

    @Test
    fun `importTours saves all when no duplicates exist`() {
        val tour1 = aMTTour("MT-001")
        val tour2 = aMTTour("MT-002").copy(STARTTIMESTAMP = startedAt.plusSeconds(3600).toEpochMilli())

        every { repository.existsByStartedAtAndDistanceAndDurationMoving(any(), any(), any()) } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns TourEntity(
            UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L)
        every { repository.saveAll(any<List<TourEntity>>()) } returns emptyList()

        service.importTours(listOf(tour1, tour2))
        verify(exactly = 1) { repository.saveAll(any<List<TourEntity>>()) }
    }

    @Test
    fun `importTours throws IMPORT_BIKE_NOT_FOUND when bikeId does not exist`() {
        val bikeId = UUID.randomUUID()
        val tour = aMTTour("MT-001").copy(bikeId = bikeId)

        every { repository.existsByStartedAtAndDistanceAndDurationMoving(any(), any(), any()) } returns false
        every { bikeRepository.existsById(bikeId) } returns false

        val ex = assertThrows(ServiceException::class.java) { service.importTours(listOf(tour)) }
        assertEquals(ErrorCodesDomain.IMPORT_BIKE_NOT_FOUND, ex.getError())
        verify(exactly = 0) { repository.saveAll(any<List<TourEntity>>()) }
    }

    @Test
    fun `importTours succeeds when bikeId is null`() {
        val tour = aMTTour("MT-001") // bikeId = null by default

        every { repository.existsByStartedAtAndDistanceAndDurationMoving(any(), any(), any()) } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns TourEntity(
            UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L)
        every { repository.saveAll(any<List<TourEntity>>()) } returns emptyList()

        service.importTours(listOf(tour))
        verify(exactly = 1) { repository.saveAll(any<List<TourEntity>>()) }
        verify(exactly = 0) { bikeRepository.existsById(any()) }
    }

    @Test
    fun `importTours succeeds when bikeId exists`() {
        val bikeId = UUID.randomUUID()
        val tour = aMTTour("MT-001").copy(bikeId = bikeId)

        every { repository.existsByStartedAtAndDistanceAndDurationMoving(any(), any(), any()) } returns false
        every { bikeRepository.existsById(bikeId) } returns true
        every { mapper.map(domain = any<DomainTour>()) } returns TourEntity(
            UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L)
        every { repository.saveAll(any<List<TourEntity>>()) } returns emptyList()

        service.importTours(listOf(tour))
        verify(exactly = 1) { repository.saveAll(any<List<TourEntity>>()) }
    }

    // MT staging dedup stays bike-independent: a bike-less MT re-import is detected as duplicate
    @Test
    fun `importTours detects duplicate regardless of bikeId (bike-independent staging dedup)`() {
        val tour = aMTTour("MT-001") // bikeId = null

        every {
            repository.existsByStartedAtAndDistanceAndDurationMoving(
                Instant.ofEpochMilli(tour.STARTTIMESTAMP), tour.DISTANCE, tour.DURATIONMOVING)
        } returns true

        val ex = assertThrows(ServiceException::class.java) { service.importTours(listOf(tour)) }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
    }

    // CE-0070: addTour with source=FIT stamps genMtId and source=FIT
    @Test
    fun `addTour stamps source FIT when caller passes FIT`() {
        val domainSlot = mutableListOf<DomainTour>()
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = aTour().copy(id = entity.id, createdAt = entity.createdAt)

        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, null)
        } returns false
        every { mapper.map(domain = capture(domainSlot)) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        service.addTour(aTour(), TourSource.FIT)

        val captured = domainSlot.first()
        assert(!captured.mtTourId.isNullOrBlank()) { "mtTourId must be generated" }
        assertEquals(TourSource.FIT, captured.source)
    }

    @Test
    fun `addTour with FIT source throws TOUR_DUPLICATE on same-bike re-create`() {
        val bikeId = UUID.randomUUID()
        val bikeEntity = BikeEntity(id = bikeId, model = "Bike A")
        val tour = aTour(bikeId = bikeId)

        every { bikeMapper.map(any<DomainBike>()) } returns bikeEntity
        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, bikeEntity)
        } returns true

        val ex = assertThrows(ServiceException::class.java) { service.addTour(tour, TourSource.FIT) }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
    }

    @Test
    fun `addTour with FIT source allows same ride on different bike`() {
        val bikeId = UUID.randomUUID()
        val bikeEntity = BikeEntity(id = bikeId, model = "Bike B")
        val tour = aTour(bikeId = bikeId)
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            bikeEntity, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = tour.copy(id = entity.id, createdAt = entity.createdAt)

        every { bikeMapper.map(any<DomainBike>()) } returns bikeEntity
        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, bikeEntity)
        } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        service.addTour(tour, TourSource.FIT)
        verify(exactly = 1) { repository.save(any()) }
    }

    // CE-0069: addTour must generate mtTourId and stamp source = MANUAL
    @Test
    fun `addTour stamps generated mtTourId and source MANUAL`() {
        val domainSlot = mutableListOf<DomainTour>()
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = aTour().copy(id = entity.id, createdAt = entity.createdAt)

        every {
            repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                startedAt, distance, durationRecorded, durationElapsed, null)
        } returns false
        every { mapper.map(domain = capture(domainSlot)) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        service.addTour(aTour())

        val captured = domainSlot.first()
        assert(!captured.mtTourId.isNullOrBlank()) { "mtTourId must be generated" }
        assertEquals(TourSource.MANUAL, captured.source)
    }
}
