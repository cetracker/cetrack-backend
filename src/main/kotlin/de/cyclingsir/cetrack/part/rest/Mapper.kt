package de.cyclingsir.cetrack.part.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.infrastructure.api.model.PartPartTypeRelation
import de.cyclingsir.cetrack.infrastructure.api.model.PartType
import de.cyclingsir.cetrack.infrastructure.api.model.ReportItem
import de.cyclingsir.cetrack.part.domain.DomainPart
import de.cyclingsir.cetrack.part.domain.DomainPartPartTypeRelation
import de.cyclingsir.cetrack.part.domain.DomainPartType
import de.cyclingsir.cetrack.part.domain.DomainReportItem
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Initially created on 1/24/23.
 */

interface PartDomain2ApiMapperSupport {
    fun mapOffsetDateTime(o: OffsetDateTime?): Instant? = o?.toInstant()
    fun mapNullableInstant2UTC(i: Instant?): OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)
}

@Mapper
interface PartDomain2ApiMapper : PartDomain2ApiMapperSupport {
    fun map(domain: DomainPart): Part

    fun map(rest: Part): DomainPart
}

@Mapper
interface PartPartTypeRelationDomain2ApiMapper : PartDomain2ApiMapperSupport {
    fun map(domain: DomainPartPartTypeRelation) : PartPartTypeRelation

    fun map(rest: PartPartTypeRelation) : DomainPartPartTypeRelation
}
@Mapper
interface PartTypeDomain2ApiMapper : PartDomain2ApiMapperSupport {
    fun map(domain: DomainPartType): PartType

    fun map(rest: PartType): DomainPartType
}

@Mapper
interface ReportDomain2ApiMapper {
    fun map(domain: DomainReportItem): ReportItem
}
