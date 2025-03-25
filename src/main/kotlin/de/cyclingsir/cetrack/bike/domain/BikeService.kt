package de.cyclingsir.cetrack.bike.domain

import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesService
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
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

    @SuppressWarnings("BC_BAD_CAST_TO_ABSTRACT_COLLECTION")
    fun getBikes(): List<DomainBike> {
        val bikeEntities: List<BikeEntity> = repository.findAll()
        return bikeEntities.map(mapper::map)
    }

    fun modifyBike(bikeId: UUID, bike: DomainBike): DomainBike? {
        val bikeEntity = try {
            assert(bikeId == bike.id)
            repository.save(mapper.map(bike))
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.BIKE_NOT_PERSISTED, e.message)
        }
        return mapper.map(bikeEntity)
    }

    fun deleteBike(bikeId: UUID) {
        try {
            repository.deleteById(bikeId)
        } catch (e: DataIntegrityViolationException) {
            e.message?.let { message ->
                if (message.contains("PART_TYPE"))
                    throw ServiceException(ErrorCodesDomain.BIKE_HAS_FOREIGN_KEY_CONSTRAINT, "Bike is connected to a part type. Delete this first.")
                if (message.contains("TOUR"))
                    throw ServiceException(ErrorCodesDomain.BIKE_HAS_FOREIGN_KEY_CONSTRAINT, "Bike is connected to at least one tour. Delete this first.")

                throw ServiceException(ErrorCodesService.INTERNAL_SERVER_ERROR)
            }
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND, e.message)
        }
    }
}
