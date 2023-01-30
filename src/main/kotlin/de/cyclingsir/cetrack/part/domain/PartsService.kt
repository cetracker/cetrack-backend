package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationRepository
import de.cyclingsir.cetrack.part.storage.PartRepository
import de.cyclingsir.cetrack.part.storage.PartTypeRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Initially created on 1/23/23.
 */
private val logger = KotlinLogging.logger {}
@Service
class PartsService(
    private val partRepository: PartRepository,
    private val partTypeRepository: PartTypeRepository,
    private val partParTypRelationRepository: PartPartTypeRelationRepository,
    private val partDomain2StorageMapper: PartDomain2StorageMapper
) {

    fun getParts(): List<DomainPart> {
        val partEntities = partRepository.findAll()
        val part = PartEntity(UUID.randomUUID(), "Lenker")
        partEntities.add(part)
        return partEntities.map(partDomain2StorageMapper::map)
    }

    fun addPart(part: DomainPart): DomainPart {
        val partEntity = partRepository.save(partDomain2StorageMapper.map(part))
        logger.info { "Added Entity: ${partEntity.createdAt?.toString()}, ${partEntity.name}" }
        val domainPart = partDomain2StorageMapper.map(partEntity)
        logger.info { "Domain Part (mapped) ${domainPart.createdAt?.toString()}"}
        return domainPart
    }

    fun getPart(partId: UUID): DomainPart? {
        val part = partRepository.findById(partId)
        return part.let {
            partDomain2StorageMapper.map(it.get())
        }
    }
}
