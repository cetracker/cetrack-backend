package de.cyclingsir.cetrack.part.storage

import com.syouth.kmapper.processor_annotations.Bind
import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.part.domain.DomainPart
import de.cyclingsir.cetrack.part.domain.DomainPartPartTypeRelation
import de.cyclingsir.cetrack.part.domain.DomainPartType
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Initially created on 1/24/23.
 */
interface PartDomain2StorageMapperSupport {
    fun mapOffsetDateTime(o: OffsetDateTime?): Instant? = o?.toInstant()
    fun mapNullableInstant2UTC(i: Instant?): OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)
}

@Mapper
interface PartDomain2StorageMapper : PartDomain2StorageMapperSupport {
    fun map(domain: DomainPart, @Bind partTypeRelations: List<PartPartTypeRelationEntity>?) : PartEntity

    fun map(jpa: PartEntity, @Bind partTypeRelations: List<DomainPartPartTypeRelation>?) : DomainPart
}

@Mapper
interface PartPartTypeRelationDomain2StorageMapper : PartDomain2StorageMapperSupport {
    fun map(domain: DomainPartPartTypeRelation, @Bind part: PartEntity, @Bind partType: PartTypeEntity) : PartPartTypeRelationEntity
    fun map(jpa: PartPartTypeRelationEntity, @Bind part: DomainPart, @Bind partType: DomainPartType) : DomainPartPartTypeRelation
}

@Mapper
interface PartTypeDomain2StorageMapper : PartDomain2StorageMapperSupport {
    fun map(domain: DomainPartType, @Bind partTypeRelations: List<PartPartTypeRelationEntity>?) : PartTypeEntity

    fun map(jpa: PartTypeEntity, @Bind partTypeRelations: List<DomainPartPartTypeRelation>?) : DomainPartType

}

@Configuration
class PartStorageMapper(
    private val partMapper: PartDomain2StorageMapper,
    private val partTypeMapper: PartTypeDomain2StorageMapper,
    private val relationMapper: PartPartTypeRelationDomain2StorageMapper) {
    fun map(domain: DomainPart): PartEntity {
        return partMapper.map(domain, domain.partTypeRelations?.map(::map2RelationEntityWithEmptyLists))
    }

    fun map(jpa: PartEntity): DomainPart {
        return partMapper.map(jpa, jpa.partTypeRelations?.map(::map2DomainRelationWithEmptyLists))
    }

    fun map(jpa: PartTypeEntity): DomainPartType {
        return partTypeMapper.map(jpa, jpa.partTypeRelations?.map(::map2DomainRelationWithEmptyLists))
    }

    fun map(domain: DomainPartType): PartTypeEntity {
        return partTypeMapper.map(domain, domain.partTypeRelations?.map(::map2RelationEntityWithEmptyLists))
    }

    fun map(domainRelation: DomainPartPartTypeRelation): PartPartTypeRelationEntity {
        return relationMapper.map(domainRelation, map(domainRelation.part), map(domainRelation.partType)) }

    private fun map2RelationEntityWithEmptyLists(relation: DomainPartPartTypeRelation) =
        relationMapper.map(
            relation,
            partMapper.map(relation.part, listOf()),
            partTypeMapper.map(relation.partType, listOf())
        )
    private fun map2DomainRelationWithEmptyLists(relation: PartPartTypeRelationEntity) =
        relationMapper.map(
            relation,
            partMapper.map(relation.part, listOf()),
            partTypeMapper.map(relation.partType, listOf())
        )
}
