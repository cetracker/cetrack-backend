package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationRepository
import de.cyclingsir.cetrack.part.storage.PartRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Initially created on 1/23/23.
 */
private val logger = KotlinLogging.logger {}

@Service
class PartService(
    private val partRepository: PartRepository,
    private val partParTypRelationRepository: PartPartTypeRelationRepository,
    private val partDomain2StorageMapper: PartDomain2StorageMapper,
    private val partPartTypeRelationMapper: PartPartTypeRelationDomain2StorageMapper
) {

    fun getParts(): List<DomainPart> {
        val partEntities = partRepository.findAll()
        return partEntities.map(partDomain2StorageMapper::map)
    }

    fun addPart(part: DomainPart): DomainPart {
        val partEntity = partRepository.save(partDomain2StorageMapper.map(part))
        logger.info { "Added Entity: ${partEntity.createdAt?.toString()}, ${partEntity.name}" }
        val domainPart = partDomain2StorageMapper.map(partEntity)
        logger.info { "Domain Part (mapped) ${domainPart.createdAt?.toString()}" }
        return domainPart
    }

    fun getPart(partId: UUID): DomainPart? {
        val part = partRepository.findById(partId)
        return part.let {
            partDomain2StorageMapper.map(it.get())
        }
    }

    /**
     * Relate a part to a part type.
     * Constraint `valid_from` <= `valid_until` will be enforced by the database
     *
     * @param relation from a part to a part type
     * @return The part a relation was added too
     */
    fun createPartPartTypeRelation(relation: DomainPartPartTypeRelation): DomainPart {
        /*
      only create relation in case
        no relation is defined for part or

        an open-ended relation is defined for the part
               unless it's the same part type id
                - in which case do nothing
               otherwise end the open-ended relation (should be one defined only!)
  */
        val partId = relation.partId
        val partTypeId = relation.partTypeId


        if (partParTypRelationRepository.countByPartId(partId).compareTo(0) == 0) {
            return createNewRelationForPart(relation);
        }
        val endOfPreviousDay = relation.validFrom
            .truncatedTo(ChronoUnit.DAYS)
            .minus(1, ChronoUnit.MINUTES);
        logger.debug("Will set previous relation to end at: $endOfPreviousDay");

        if (partParTypRelationRepository.countByPartIdAndValidUntilIsNull(partId) > 0) {
            return if (partParTypRelationRepository.countByPartIdAndPartTypeIdAndValidUntilIsNull(partId, partTypeId) > 0) {
                // relation already present - do nothing (a potential different validFrom will be ignored)
                logger.debug("Open-ended relation with same part and part type found");
                val partEntity = partRepository.findById(partId);
                partDomain2StorageMapper.map(partEntity.get())
            } else {
                // set to one day before the new relation starts
                endOpenEndedRelationForPartAtDate(partId, endOfPreviousDay);

                createNewRelationForPart(relation);
            }
        } else {
            logger.warn("Most recent relation was not open-ended!");
            // set to one day before the new relation starts
            modifyValidityOfYoungestRelationForPartAtDate(partId, endOfPreviousDay);

            return createNewRelationForPart(relation);
        }
    }

    private fun createNewRelationForPart(relation: DomainPartPartTypeRelation): DomainPart {
        logger.debug("Going to persist relation $relation")
        val relationEntity = partPartTypeRelationMapper.map(relation)
        val createdRelation: PartPartTypeRelationEntity = partParTypRelationRepository.save(relationEntity)
        val part: PartEntity = partRepository.findById(createdRelation.partId).get()
        logger.info("Newly related part: ${part.id} ${part.name} with ${part.partTypes.size} PartTypes")
        return partDomain2StorageMapper.map(part)
    }

    private fun endOpenEndedRelationForPartAtDate(partId: UUID, validUntil: Instant) {
        val openEndedRelation: PartPartTypeRelationEntity? =
            partParTypRelationRepository.findFirstByPartIdAndValidUntilIsNull(partId)
        modifyRelation(validUntil, openEndedRelation, partId)
    }

    /**
     * Modify validity end of most recent valid relation for the part on a given date.
     *
     * @param partId Part for which to end the relation
     * @param validUntil instant, on which day to end the relation
     */
    private fun modifyValidityOfYoungestRelationForPartAtDate(partId: UUID, validUntil: Instant) {
        val youngestRelation: PartPartTypeRelationEntity? =
            partParTypRelationRepository.findFirstByPartIdAndValidUntilIsNotNullOrderByValidUntilDesc(partId)
        modifyRelation(validUntil, youngestRelation, partId)
    }


    private fun modifyRelation(
        validUntil: Instant,
        relation: PartPartTypeRelationEntity?,
        partId: UUID
    ) {
        if (relation == null) {
            throw ServiceException(
                ErrorCodesDomain.PREVIOUS_RELATION_NOT_FOUND,
                "No open-ended relation for $partId found")

        } else {
            try {
                if (relation.validUntil != null) {
                    if (validUntil.isBefore(relation.validUntil)) {
                        logger.warn("Set relation's validity back in time: $validUntil")
                    } else {
                        logger.warn("Set relation's validity forward in time: $validUntil")
                    }
                    relation.validUntil = validUntil
                    partParTypRelationRepository.save(relation)
                }
            } catch (ex: Exception) {
                logger.debug("Persisting failed: $ex")
                throw ServiceException(ErrorCodesDomain.RELATION_NOT_VALID, ex)
            }
        }
    }
}
