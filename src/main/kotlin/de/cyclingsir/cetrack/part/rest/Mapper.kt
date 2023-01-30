package de.cyclingsir.cetrack.part.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.infrastructure.api.model.Part
import de.cyclingsir.cetrack.part.domain.DomainPart
import de.cyclingsir.cetrack.part.storage.PartEntity
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

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
