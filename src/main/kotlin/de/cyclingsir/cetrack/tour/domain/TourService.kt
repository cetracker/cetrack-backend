package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.storage.TourDomain2StorageMapper
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class TourService(
    private val repository: TourRepository,
    private val mapper: TourDomain2StorageMapper,
    private val bikeMapper: BikeDomain2StorageMapper,
    private val bikeService: BikeService,
    private val bikeRepository: BikeRepository
) {

    @Transactional
    fun addTour(tour: DomainTour, source: TourSource = TourSource.MANUAL): DomainTour {
        val stamped = tour.copy(mtTourId = genMtId(tour.startedAt, tour.distance), source = source)
        val bikeEntity = stamped.bike?.let { bikeMapper.map(it) }
        if (repository.existsByStartedAtAndDistanceAndDurationRecordedAndDurationElapsedAndBike(
                stamped.startedAt, stamped.distance, stamped.durationRecorded, stamped.durationElapsed, bikeEntity)) {
            throw ServiceException(
                ErrorCodesDomain.TOUR_DUPLICATE,
                "Tour starting at ${stamped.startedAt} with distance ${stamped.distance}m and device times ${stamped.durationRecorded}/${stamped.durationElapsed}s already exists"
            )
        }
        val tourEntity = repository.save(mapper.map(stamped))
        logger.info { "Added Entity = mtTour., ${tourEntity.title}" }
        val domainTour = mapper.map(tourEntity)
        logger.info { "Domain Tour (mapped) ${domainTour.createdAt?.toString()}" }
        return domainTour
    }

    @Transactional(readOnly = true)
    fun getTour(tourId: UUID) : DomainTour {
        val tourEntity = repository.findById(tourId).get()
        return mapper.map(jpa = tourEntity)
    }

    @Transactional(readOnly = true)
    fun getTours(): List<DomainTour> {
        val tourEntities: List<TourEntity> = repository.findAll()
        return tourEntities.map(mapper::map)
    }

    @Transactional
    fun importTours(tours: List<DomainMTTour>) {
        val duplicates = tours.filter {
            val startedAt = Instant.ofEpochMilli(it.STARTTIMESTAMP)
            repository.existsByStartedAtAndDistanceAndDurationMoving(startedAt, it.DISTANCE, it.DURATIONMOVING)
        }
        if (duplicates.isNotEmpty()) {
            throw ServiceException(
                ErrorCodesDomain.TOUR_DUPLICATE,
                "Duplicate tours detected: ${duplicates.joinToString { it.MTTOURID }}"
            )
        }
        tours.mapNotNull { it.bikeId }.distinct().forEach { id ->
            if (!bikeRepository.existsById(id))
                throw ServiceException(ErrorCodesDomain.IMPORT_BIKE_NOT_FOUND, "Bike $id referenced in import not found")
        }
        val domainTours = tours.map(this::mapMTTour2Tour)
        repository.saveAll(domainTours.map(mapper::map))
    }

    private fun mapMTTour2Tour(mtTour: DomainMTTour) : DomainTour {
        val instantStarted = Instant.ofEpochMilli(mtTour.STARTTIMESTAMP)
        return DomainTour(
            id = null, // @GeneratedValue(strategy = GenerationType.UUID) don't tamper with the generator
            mtTourId = mtTour.MTTOURID,
            title = mtTour.TITLE,
            distance = mtTour.DISTANCE,
            durationMoving = mtTour.DURATIONMOVING,
            durationRecorded = mtTour.TIMERECORDEDDEVICE ?: 0L,
            durationElapsed = mtTour.TIMEELAPSEDDEVICE ?: 0L,
            ascent = mtTour.TOURALTUP,
            descent = mtTour.TOURALTDOWN,
            powerTotal = mtTour.POWERTOTAL,
            startedAt = instantStarted,
            startYear = mtTour.STARTYEAR,
            startMonth = mtTour.STARTMONTH,
            startDay = mtTour.STARTDAY,
            bike = DomainBike(id = mtTour.bikeId),
            createdAt = null,
            source = TourSource.MYTOURBOOK
        )
    }

    @Transactional
    fun relateTourToBike(tourId: UUID, bikeId: UUID): DomainTour {
        val domainBike = bikeService.getBike(bikeId)
        val tourEntity = repository.findById(tourId).get()
        tourEntity.bike = bikeMapper.map(domainBike)
        val modifiedEntity = try {
            repository.save(tourEntity)
        } catch (e: Exception) {
            logger.warn { "Add bike to tour ${e.message}" }
            throw e
        }
        return mapper.map(modifiedEntity)

    }
}
