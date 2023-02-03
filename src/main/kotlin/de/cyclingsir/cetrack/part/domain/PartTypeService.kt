package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Initially created on 1/31/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class PartTypeService(
    private val repository: PartTypeRepository,
    private val partTypeDomain2StorageMapper: PartTypeDomain2StorageMapper,
    private val bikeMapper: BikeDomain2StorageMapper,
    private val bikeService: BikeService
) {
    fun addPartType(partType: DomainPartType): DomainPartType {
        val partTypeEntity = repository.save(partTypeDomain2StorageMapper.map(partType))
        logger.info { "Added Entity: ${partTypeEntity.createdAt?.toString()}, ${partTypeEntity.name}" }
        val domainPart = partTypeDomain2StorageMapper.map(partTypeEntity)
        logger.info { "Domain PartType (mapped) ${domainPart.createdAt?.toString()}" }
        return domainPart
    }

    fun getPartTypes(): List<DomainPartType> {
        val partTypeEntities = repository.findAll()
        return partTypeEntities.map(partTypeDomain2StorageMapper::map)
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
        return partTypeDomain2StorageMapper.map(modifiedEntity)
    }
}
