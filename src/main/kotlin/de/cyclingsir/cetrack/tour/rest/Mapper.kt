package de.cyclingsir.cetrack.tour.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.infrastructure.api.model.MTTour
import de.cyclingsir.cetrack.infrastructure.api.model.Tour
import de.cyclingsir.cetrack.tour.domain.DomainTour
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Initially created on 2/1/23.
 */

interface TourDomain2ApiMapperSupport {
    fun mapNullableOffsetDateTime(o: OffsetDateTime?): Instant? = o?.toInstant()
    fun mapNullableInstant2UTC(i: Instant?): OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)
    fun mapInt2Duration(l: Long): Duration = Duration.ofSeconds(l)
    fun mapDuration2Int(d: Duration): Long = d.toSeconds()
/*
    fun mapNullableBike2NullableBikeId(b: DomainBike?): UUID? = b?.id
    fun mapNullableBikeId2NullableDomainBike(id: UUID?): DomainBike? = id?.let{
        DomainBike("", "", id = it, null, null) }
    fun mapOffsetDateTime(o: OffsetDateTime): Instant = o.toInstant()
    fun mapInstant2UTC(i: Instant): OffsetDateTime = i.atOffset(ZoneOffset.UTC)
*/
}

@Mapper
interface TourDomain2ApiMapper : TourDomain2ApiMapperSupport {

    fun map(domain: DomainTour) : Tour

    fun map(rest: Tour) : DomainTour
}

@Mapper
interface MTTourDomain2ApiMapper  {
    fun map(rest: MTTour) : DomainMTTour
}
