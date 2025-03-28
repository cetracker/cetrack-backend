package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.part.storage.PartEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationRepository
import de.cyclingsir.cetrack.part.storage.PartRepository
import de.cyclingsir.cetrack.part.storage.PartStorageMapper
import de.cyclingsir.cetrack.part.storage.ReportProjectionComplete
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Initially created on 1/23/23.
 */
private val logger = KotlinLogging.logger {}

@Service
final class PartService(
    private val partRepository: PartRepository,
    private val partParTypRelationRepository: PartPartTypeRelationRepository,
    private val mapper: PartStorageMapper
) {

    fun getParts(): List<DomainPart> {
        // toList() makes it immutable as suggested by SpotBugs
        val partEntities = partRepository.findAll().toList()
        return partEntities.map(mapper::map)
    }

    fun getReport() : List<DomainReportItem>{
        var reportList : List<DomainReportItem> = mutableListOf()
        try {
            val report: Collection<ReportProjectionComplete> = partRepository.getCompleteReport()
            report.forEach { r ->
                logger.debug { "${r.partName} | ${r.meterTotal} | ${r.secondsTotal} | ${r.powerTotal}" }
            }
            logger.info {"$report"}
            reportList = report.map { r ->
                DomainReportItem(r.partName, r.meterTotal.toLong(), r.secondsTotal,
                    r.altUpTotal.toLong(), r.altDownTotal.toLong(), r.powerTotal)
            }
        } catch (e: Exception) {
            logger.warn { e }
            e.printStackTrace()
        }
        return reportList
    }

    fun addPart(part: DomainPart): DomainPart {
        val partEntity = partRepository.save(mapper.map(part))
        logger.info { "Added Entity: ${partEntity.createdAt?.toString()}, ${partEntity.name}" }
        val domainPart = mapper.map(partEntity)
        logger.info { "Domain Part (mapped) ${domainPart.createdAt?.toString()}" }
        return domainPart
    }

    fun modifyPart(partId: UUID, part: DomainPart): DomainPart? {
        logger.debug { "Modify Part for part ${part} was called!" }
        assert(partId == part.id)
        logger.debug { "DomainPart: ${part}" }
        val entity = mapper.map(part)
        logger.debug { "Mapped Entity: ${entity}" }

        val partEntity = try {
            partRepository.save(entity)
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.RELATION_NOT_VALID, e.message)
        }
        logger.info { "Modified Part Entity: ${partEntity.createdAt?.toString()}, ${partEntity.name} - ${partEntity.partTypeRelations?.size}" }
        return mapper.map(partEntity)
    }

    fun deletePart(partId: UUID) {
        try {
            partRepository.deleteById(partId)
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.PART_NOT_FOUND, e.message)
        }
    }

    fun getPart(partId: UUID): DomainPart? {
        val part = partRepository.findById(partId)
        return part.let {
            mapper.map(it.get())
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


        if (partParTypRelationRepository.countByPartId(partId).compareTo(0) == 0  &&
                partParTypRelationRepository.countByPartTypeIdAndValidUntilIsNull(partTypeId).compareTo(0) == 0) {
            return createNewRelationForPart(relation)
        }
        val endOfPreviousDay = relation.validFrom
            .truncatedTo(ChronoUnit.DAYS)
            .minus(1, ChronoUnit.SECONDS).toInstant()
        logger.debug {"Will set previous relation to end at: $endOfPreviousDay"}

        return if (partParTypRelationRepository.countByPartIdAndValidUntilIsNull(partId) > 0) {
            if (partParTypRelationRepository.countByPartIdAndPartTypeIdAndValidUntilIsNull(partId, partTypeId) > 0) {
                // relation already present - do nothing (a potential different validFrom will be ignored)
                logger.debug {"Open-ended relation with same part and part type found"}
                val partEntity = partRepository.findById(partId)
                mapper.map(partEntity.get())
            } else {
                throw ServiceException(
                    ErrorCodesDomain.PREVIOUS_RELATION_NOT_FOUND,
                    "No open-ended relation for $partId <-> $partTypeId found")
            }
        } else {
            if (partParTypRelationRepository.countByPartTypeIdAndValidUntilIsNull(partTypeId) > 0) {
                val currentActiveRelation =
                    partParTypRelationRepository.findFirstByPartTypeIdAndValidUntilIsNull(partTypeId)
                modifyRelation(endOfPreviousDay, currentActiveRelation)

                createNewRelationForPart(relation)
            } else {
                logger.warn { "Most recent relation was not open-ended!" }
                // set to one day before the new relation starts
                // ToDo is this desirable??
                modifyValidityOfYoungestRelationForPartAtDate(partId, endOfPreviousDay)

                createNewRelationForPart(relation)
            }
        }
    }

    private fun createNewRelationForPart(relation: DomainPartPartTypeRelation): DomainPart {
        logger.debug {"Going to persist relation ${relation}"}
        val relationEntity = mapper.map(relation)
        val createdRelation: PartPartTypeRelationEntity = partParTypRelationRepository.save(relationEntity)
        val part: PartEntity = partRepository.findById(createdRelation.partId).get()
        logger.info {"Newly related part: ${part.id} ${part.name} with ${part.partTypeRelations?.size} PartTypeRelations"}
        return mapper.map(part)
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
        modifyRelation(validUntil, youngestRelation)
    }


    private fun modifyRelation(
        validUntil: Instant,
        relation: PartPartTypeRelationEntity?,
    ) {
        if (relation == null) {
            throw ServiceException(
                ErrorCodesDomain.PREVIOUS_RELATION_NOT_FOUND,
                "No open-ended relation for ${relation?.partId} found")

        } else {
            try {
                if (relation.validUntil != null) {
                    if (validUntil.isBefore(relation.validUntil)) {
                        logger.warn {"Set relation's validity back in time: $validUntil"}
                    } else {
                        logger.warn {"Set relation's validity forward in time: $validUntil"}
                    }
                    relation.validUntil = validUntil
                    partParTypRelationRepository.save(relation)
                } else {
                    logger.debug {
                      "Terminate open ended relation for ${relation.partId} to ${relation.partTypeId} (${relation.partType.name}) at $validUntil "
                    }
                    relation.validUntil = validUntil
                    partParTypRelationRepository.save(relation)
                }
            } catch (ex: Exception) {
                logger.debug {"Persisting failed: ${ex.message}"}
                throw ServiceException(ErrorCodesDomain.RELATION_NOT_VALID, ex)
            }
        }
    }
}
