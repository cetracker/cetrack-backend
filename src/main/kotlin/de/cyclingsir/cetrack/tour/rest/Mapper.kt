package de.cyclingsir.cetrack.tour.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.infrastructure.api.model.MTTour
import de.cyclingsir.cetrack.infrastructure.api.model.Tour
import de.cyclingsir.cetrack.infrastructure.api.model.TourCreateRequest
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

    fun map(rest: TourCreateRequest): DomainTour = DomainTour(
        id = null,
        mtTourId = null,
        title = rest.title,
        distance = rest.distance,
        durationMoving = rest.durationMoving,
        durationRecorded = rest.durationRecorded,
        durationElapsed = rest.durationElapsed,
        altUp = rest.altUp,
        altDown = rest.altDown,
        powerTotal = rest.powerTotal,
        bike = DomainBike(
            model = rest.bike.model,
            manufacturer = rest.bike.manufacturer,
            id = rest.bike.id,
            boughtAt = rest.bike.boughtAt?.toInstant(),
            retiredAt = rest.bike.retiredAt?.toInstant(),
            createdAt = rest.bike.createdAt?.toInstant()
        ),
        startedAt = rest.startedAt.toInstant(),
        startYear = rest.startYear,
        startMonth = rest.startMonth,
        startDay = rest.startDay,
        createdAt = null
    )
}

@Mapper
fun interface MTTourDomain2ApiMapper  {
    fun map(rest: MTTour) : DomainMTTour
}
