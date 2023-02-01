package de.cyclingsir.cetrack.bike.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.infrastructure.api.model.Bike
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Initially created on 2/1/23.
 */

interface BikeDomain2ApiMapperSupport {
    fun mapOffsetDateTime(o: OffsetDateTime?): Instant? = o?.toInstant()
    fun mapNullableInstant2UTC(i: Instant?): OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)
}

@Mapper
interface BikeDomain2ApiMapper : BikeDomain2ApiMapperSupport {

    fun map(domain: DomainBike) : Bike

    fun map(rest: Bike) : DomainBike
}
