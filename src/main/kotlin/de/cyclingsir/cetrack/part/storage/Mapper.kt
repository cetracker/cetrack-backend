package de.cyclingsir.cetrack.part.storage

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.part.domain.DomainPart
import de.cyclingsir.cetrack.part.domain.DomainPartPartTypeRelation
import de.cyclingsir.cetrack.part.domain.DomainPartType
import java.util.UUID

/**
 * Initially created on 1/24/23.
 */
interface PartDomain2StorageMapperSupport {
    fun mapNullableUUIDToUUID(i: UUID?): UUID = i ?: UUID.randomUUID()
}

@Mapper
interface PartDomain2StorageMapper : PartDomain2StorageMapperSupport {
    fun map(domain: DomainPart) : PartEntity

    fun map(jpa: PartEntity) : DomainPart
}

@Mapper
interface PartPartTypeRelationDomain2StorageMapper {
    fun map(domain: DomainPartPartTypeRelation) : PartPartTypeRelationEntity
    fun map(domain: PartPartTypeRelationEntity) : DomainPartPartTypeRelation
}

@Mapper
interface PartTypeDomain2StorageMapper : PartDomain2StorageMapperSupport {
    fun map(domain: DomainPartType) : PartTypeEntity

    fun map(jpa: PartTypeEntity) : DomainPartType
}
