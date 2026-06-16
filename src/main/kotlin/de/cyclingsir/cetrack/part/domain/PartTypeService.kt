package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesService
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import org.springframework.dao.DataIntegrityViolationException
import de.cyclingsir.cetrack.part.storage.PartStorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeEntity
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
        val partTypeEntities: List<PartTypeEntity> = repository.findAll()
        return partTypeEntities.map(mapper::map)
    }

    fun modifyPartType(partTypeId: UUID, partType: DomainPartType): DomainPartType? {
        if (partType.id != null && partType.id != partTypeId) {
            throw ServiceException(ErrorCodesDomain.PART_TYPE_ID_MISMATCH)
        }
        if (!repository.existsById(partTypeId)) {
            throw ServiceException(ErrorCodesDomain.PART_TYPE_NOT_FOUND)
        }
        val entity = mapper.map(partType)
        entity.id = partTypeId
        val partTypeEntity = try {
            repository.save(entity)
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.PART_TYPE_NOT_PERSISTED, e.message ?: "Persisting failed", e)
        }
        return mapper.map(partTypeEntity)
    }

    fun deletePartType(partTypeId: UUID) {
        if (!repository.existsById(partTypeId)) throw ServiceException(ErrorCodesDomain.PART_TYPE_NOT_FOUND)
        try {
            repository.deleteById(partTypeId)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.PART_TYPE_HAS_FOREIGN_KEY_CONSTRAINT,
                "PartType is referenced by a part relation. Remove it first.")
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesService.INTERNAL_SERVER_ERROR)
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
