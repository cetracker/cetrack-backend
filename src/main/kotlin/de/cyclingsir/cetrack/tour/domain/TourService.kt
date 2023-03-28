package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.tour.storage.TourDomain2StorageMapper
import de.cyclingsir.cetrack.tour.storage.TourRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
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
    private val bikeService: BikeService
) {

    fun addTour(tour: DomainTour): DomainTour {
        val tourEntity = repository.save(mapper.map(tour))
        logger.info { "Added Entity = mtTour., ${tourEntity.title}" }
        val domainTour = mapper.map(tourEntity)
        logger.info { "Domain Tour (mapped) ${domainTour.createdAt?.toString()}" }
        return domainTour
    }

    fun getTour(tourId: UUID) : DomainTour {
        val tourEntity = repository.findById(tourId).get()
        return mapper.map(jpa = tourEntity)
    }

    fun getTours(): List<DomainTour> {
        val tourEntities = repository.findAll()
        return tourEntities.map(mapper::map)
    }

    fun importTours(tours: List<DomainMTTour>) {

        val domainTours = tours.map(this::mapMTTour2Tour)
        repository.saveAll(domainTours.map(mapper::map))
    }

    private fun mapMTTour2Tour(mtTour: DomainMTTour) : DomainTour {
        val instantStarted = Instant.ofEpochMilli(mtTour.STARTTIMESTAMP)
        return DomainTour(
            id = UUID.randomUUID(),
            mtTourId = mtTour.MTTOURID,
            title = mtTour.TITLE,
            distance = mtTour.DISTANCE,
            durationMoving = mtTour.DURATIONMOVING,
            altUp = mtTour.TOURALTUP,
            altDown = mtTour.TOURALTDOWN,
            startedAt = instantStarted,
            bike = DomainBike("", null, mtTour.bikeId, null, null),
            createdAt = null
        )
    }

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
