package de.cyclingsir.cetrack.bike.domain

import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class BikeService(private val repository: BikeRepository, private val mapper: BikeDomain2StorageMapper) {

    fun addBike(bike: DomainBike): DomainBike {
        val bikeEntity = repository.save(mapper.map(bike))
        logger.info { "Added Entity: ${bikeEntity.createdAt?.toString()}, ${bikeEntity.model}" }
        val domainBike = mapper.map(bikeEntity)
        logger.info { "Domain Bike (mapped) ${domainBike.createdAt?.toString()}" }
        return domainBike
    }

    fun getBike(bikeId: UUID) : DomainBike {
        val bikeEntity = repository.findById(bikeId).get()
        return mapper.map(jpa = bikeEntity);
    }

    fun getBikes(): List<DomainBike> {
        val bikeEntities = repository.findAll()
        return bikeEntities.map(mapper::map)
    }

    fun modifyBike(bikeId: UUID, bike: DomainBike): DomainBike? {
        val bikeEntity = try {
            assert(bikeId == bike.id)
            repository.save(mapper.map(bike))
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.BIKE_NOT_PERISTED, e.message)
        }
        return mapper.map(bikeEntity)
    }

    fun deleteBike(bikeId: UUID) {
        try {
            repository.deleteById(bikeId)
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND, e.message)
        }
    }
}
