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
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    fun modifyBike(bikeId: UUID, bike: DomainBike): DomainBike {
        if (bike.id != null && bike.id != bikeId) {
            throw ServiceException(ErrorCodesDomain.BIKE_ID_MISMATCH)
        }
        val existing = repository.findById(bikeId)
            .orElseThrow { ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND) }
        val incoming = mapper.map(bike)
        existing.model = incoming.model
        existing.manufacturer = incoming.manufacturer
        existing.boughtAt = incoming.boughtAt
        existing.retiredAt = incoming.retiredAt
        val bikeEntity = try {
            repository.saveAndFlush(existing)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.BIKE_DATA_INVALID, e.message ?: "Invalid bike data", e)
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesService.INTERNAL_SERVER_ERROR, e.message ?: "Persisting failed", e)
        }
        return mapper.map(bikeEntity)
    }

    fun deleteBike(bikeId: UUID) {
        if (!repository.existsById(bikeId)) throw ServiceException(ErrorCodesDomain.BIKE_NOT_FOUND)
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
