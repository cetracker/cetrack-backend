package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
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

    private lateinit var service: TourService

    private val startedAt = Instant.parse("2024-06-01T08:00:00Z")
    private val distance = 50000
    private val durationMoving = 7200L

    private fun aTour() = DomainTour(
        id = null,
        title = "Morning Ride",
        distance = distance,
        durationMoving = durationMoving,
        altUp = 500,
        altDown = 500,
        powerTotal = 0L,
        bike = null,
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
        service = TourService(repository, mapper, bikeMapper, bikeService)
    }

    @Test
    fun `addTour throws ServiceException when tour already exists`() {
        every {
            repository.existsByStartedAtAndDistanceAndDurationMoving(startedAt, distance, durationMoving)
        } returns true

        val ex = assertThrows(ServiceException::class.java) { service.addTour(aTour()) }
        assertEquals(ErrorCodesDomain.TOUR_DUPLICATE, ex.getError())
    }

    @Test
    fun `addTour saves tour when no duplicate exists`() {
        val entity = TourEntity(UUID.randomUUID(), null, "Morning Ride", distance, durationMoving,
            null, startedAt, 2024.toShort(), 6.toShort(), 1.toShort(), 500, 500, 0L, Instant.now())
        val saved = aTour().copy(id = entity.id, createdAt = entity.createdAt)

        every { repository.existsByStartedAtAndDistanceAndDurationMoving(startedAt, distance, durationMoving) } returns false
        every { mapper.map(domain = any<DomainTour>()) } returns entity
        every { repository.save(any()) } returns entity
        every { mapper.map(jpa = any()) } returns saved

        val result = service.addTour(aTour())
        assertEquals(entity.id, result.id)
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
}
