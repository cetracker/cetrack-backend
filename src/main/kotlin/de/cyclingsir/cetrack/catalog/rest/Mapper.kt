package de.cyclingsir.cetrack.catalog.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.catalog.domain.DomainComponentType
import de.cyclingsir.cetrack.catalog.domain.DomainPosition
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentType
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentTypeInput
import de.cyclingsir.cetrack.infrastructure.api.model.Position
import de.cyclingsir.cetrack.infrastructure.api.model.PositionInput
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

interface CatalogDomain2ApiMapperSupport {
    fun mapNullableOffsetDateTime(o: OffsetDateTime?): Instant? = o?.toInstant()
    fun mapNullableInstant2UTC(i: Instant?): OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)
}

@Mapper
interface CatalogDomain2ApiMapper : CatalogDomain2ApiMapperSupport {

    fun map(domain: DomainComponentType): ComponentType

    fun map(rest: ComponentTypeInput): DomainComponentType

    fun map(domain: DomainPosition): Position

    fun map(rest: PositionInput): DomainPosition
}
