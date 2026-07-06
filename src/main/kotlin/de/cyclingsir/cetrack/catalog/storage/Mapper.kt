package de.cyclingsir.cetrack.catalog.storage

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.catalog.domain.DomainPosition

@Mapper
interface CatalogDomain2StorageMapper {
    fun map(domain: DomainComponentType): ComponentTypeEntity

    fun map(jpa: ComponentTypeEntity): DomainComponentType

    fun map(domain: DomainPosition): PositionEntity

    fun map(jpa: PositionEntity): DomainPosition
}
