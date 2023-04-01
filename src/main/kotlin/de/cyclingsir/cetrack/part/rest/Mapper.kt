package de.cyclingsir.cetrack.part.rest

import com.syouth.kmapper.processor_annotations.Bind
import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.infrastructure.api.model.PartPartTypeRelation
import de.cyclingsir.cetrack.infrastructure.api.model.PartType
import de.cyclingsir.cetrack.infrastructure.api.model.ReportItem
import de.cyclingsir.cetrack.part.domain.DomainPart
import de.cyclingsir.cetrack.part.domain.DomainPartPartTypeRelation
import de.cyclingsir.cetrack.part.domain.DomainPartType
import de.cyclingsir.cetrack.part.domain.DomainReportItem
import org.springframework.stereotype.Component
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
    fun map(domain: DomainPart, @Bind partTypeRelations: List<PartPartTypeRelation>?): Part

    fun map(rest: Part, @Bind partTypeRelations: List<DomainPartPartTypeRelation>?): DomainPart
}

@Mapper
interface PartPartTypeRelationDomain2ApiMapper : PartDomain2ApiMapperSupport {
    fun map(domain: DomainPartPartTypeRelation, @Bind part: Part, @Bind partType: PartType) : PartPartTypeRelation

    fun map(rest: PartPartTypeRelation, @Bind part: DomainPart, @Bind partType: DomainPartType) : DomainPartPartTypeRelation
}
@Mapper
interface PartTypeDomain2ApiMapper : PartDomain2ApiMapperSupport {
    fun map(domain: DomainPartType, @Bind partTypeRelations: List<PartPartTypeRelation>?): PartType

    fun map(rest: PartType, @Bind partTypeRelations: List<DomainPartPartTypeRelation>?): DomainPartType
}

@Mapper
interface ReportDomain2ApiMapper {
    fun map(domain: DomainReportItem): ReportItem
}

@Component
class PartApiMapper(
    private val partMapper: PartDomain2ApiMapper,
    private val partTypeMapper: PartTypeDomain2ApiMapper,
    private val relationMapper: PartPartTypeRelationDomain2ApiMapper,
) {
    fun map(api: Part): DomainPart {
        return partMapper.map(api, api.partTypeRelations?.map(::map2DomainRelationWithEmptyLists))
    }
    fun map(api: PartType): DomainPartType {
        return partTypeMapper.map(api, api.partTypeRelations?.map(::map2DomainRelationWithEmptyLists))
    }
    fun map(domain: DomainPart): Part {
        return partMapper.map(domain, domain.partTypeRelations?.map(::map2ApiRelationWithEmptyLists))
    }
    fun map(domain: DomainPartType): PartType {
        return partTypeMapper.map(domain, domain.partTypeRelations?.map(::map2ApiRelationWithEmptyLists))
    }

    fun map(api: PartPartTypeRelation): DomainPartPartTypeRelation {
        return relationMapper.map(api, map(api.part), map(api.partType)) }

    private fun map2DomainRelationWithEmptyLists(relation: PartPartTypeRelation) =
        relationMapper.map(
            relation,
            partMapper.map(relation.part, listOf()),
            partTypeMapper.map(relation.partType, listOf())
        )
    private fun map2ApiRelationWithEmptyLists(relation: DomainPartPartTypeRelation) =
        relationMapper.map(
            relation,
            partMapper.map(relation.part, listOf()),
            partTypeMapper.map(relation.partType, listOf())
        )
}
/*
private fun <E> List<E>.map(): DomainPartPartTypeRelation {
    TODO("Not yet implemented")
}*/
