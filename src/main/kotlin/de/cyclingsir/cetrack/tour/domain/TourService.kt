package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.tour.storage.TourDomain2StorageMapper
import de.cyclingsir.cetrack.tour.storage.TourRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class TourService(private val repository: TourRepository, private val mapper: TourDomain2StorageMapper) {

    fun addTour(Tour: DomainTour): DomainTour {
        val TourEntity = repository.save(mapper.map(Tour))
        logger.info { "Added Entity: ${TourEntity.createdAt?.toString()}, ${TourEntity.title}" }
        val domainTour = mapper.map(TourEntity)
        logger.info { "Domain Tour (mapped) ${domainTour.createdAt?.toString()}" }
        return domainTour
    }

    fun getTour(TourId: UUID) : DomainTour {
        val TourEntity = repository.findById(TourId).get()
        return mapper.map(jpa = TourEntity);
    }

    fun getTours(): List<DomainTour> {
        val TourEntities = repository.findAll()
        return TourEntities.map(mapper::map)
    }
}
