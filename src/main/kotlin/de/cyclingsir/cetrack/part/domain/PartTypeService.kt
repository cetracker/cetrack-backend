package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.part.storage.PartTypeDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Initially created on 1/31/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class PartTypeService(
    private val repository: PartTypeRepository,
    private val partTypeDomain2StorageMapper: PartTypeDomain2StorageMapper,
) {
    fun addPartType(partType: DomainPartType): DomainPartType {
        val partTypeEntity = repository.save(partTypeDomain2StorageMapper.map(partType))
        logger.info { "Added Entity: ${partTypeEntity.createdAt?.toString()}, ${partTypeEntity.name}" }
        val domainPart = partTypeDomain2StorageMapper.map(partTypeEntity)
        logger.info { "Domain Part (mapped) ${domainPart.createdAt?.toString()}" }
        return domainPart
    }

    fun getPartTypes(): List<DomainPartType> {
        val partTypeEntities = repository.findAll()
        return partTypeEntities.map(partTypeDomain2StorageMapper::map)
    }
}
