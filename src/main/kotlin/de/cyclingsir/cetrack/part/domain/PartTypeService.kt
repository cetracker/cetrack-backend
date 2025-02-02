package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.part.storage.PartStorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Initially created on 1/31/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class PartTypeService(
    private val repository: PartTypeRepository,
    private val mapper: PartStorageMapper,
    private val bikeMapper: BikeDomain2StorageMapper,
    private val bikeService: BikeService
) {
    fun addPartType(partType: DomainPartType): DomainPartType {
        val partTypeEntity = repository.save(mapper.map(partType))
        logger.info { "Added Entity: ${partTypeEntity.createdAt?.toString()}, ${partTypeEntity.name}" }
        val domainPart = mapper.map(partTypeEntity)
        logger.info { "Domain PartType (mapped) ${domainPart.createdAt?.toString()}" }
        return domainPart
    }

    fun getPartTypes(): List<DomainPartType> {
        val partTypeEntities = repository.findAll()
        return partTypeEntities.map(mapper::map)
    }

    fun modifyPartType(partTypeId: UUID, partType: DomainPartType): DomainPartType? {
        val partTypeEntity = try {
            assert(partTypeId == partType.id)
            repository.save(mapper.map(partType))
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.PART_TYPE_NOT_PERSISTED, e.message)
        }
        return mapper.map(partTypeEntity)
    }

    fun deletePartType(partTypeId: UUID) {
        try {
            repository.deleteById(partTypeId)
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.PART_TYPE_NOT_FOUND, e.message)
        }
    }

    fun getPartType(partTypeId: UUID): DomainPartType? {
        val part = repository.findById(partTypeId)
        return part.let {
            mapper.map(it.get())
        }
    }

    fun relatePartTypeToBike(partTypeId: UUID, bikeId: UUID): DomainPartType {
        val domainBike = bikeService.getBike(bikeId)
        val partTypeEntity = repository.findById(partTypeId).get()
        partTypeEntity.bike = bikeMapper.map(domainBike)
        val modifiedEntity = try {
            repository.save(partTypeEntity)
        } catch (e: Exception) {
            logger.warn { "Add bike to part ${e.message}" }
            throw e
        }
        return mapper.map(modifiedEntity)
    }
}
