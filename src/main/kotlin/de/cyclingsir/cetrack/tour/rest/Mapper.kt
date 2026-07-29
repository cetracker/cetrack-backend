package de.cyclingsir.cetrack.tour.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.common.domain.DomainRetirementKind
import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import de.cyclingsir.cetrack.infrastructure.api.model.MTTour
import de.cyclingsir.cetrack.infrastructure.api.model.RetirementKind
import de.cyclingsir.cetrack.infrastructure.api.model.Tour
import de.cyclingsir.cetrack.infrastructure.api.model.TourCreateRequest
import de.cyclingsir.cetrack.tour.domain.DomainTour
import de.cyclingsir.cetrack.tour.domain.TourSource
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
    fun mapDomainSource2Api(s: TourSource): Tour.Source = Tour.Source.valueOf(s.name)
    fun mapApiSource2Domain(s: Tour.Source?): TourSource = s?.let { TourSource.valueOf(it.name) } ?: TourSource.MYTOURBOOK

    /** Nested in DomainTour.bike/Tour.bike; kmapper can't auto-convert enum<->enum (CE-0126). */
    fun mapDomainRetirementKind2Api(kind: DomainRetirementKind?): RetirementKind? = when (kind) {
        null -> null
        DomainRetirementKind.SCRAPPED -> RetirementKind.scrapped
        DomainRetirementKind.SOLD -> RetirementKind.sold
        DomainRetirementKind.GIFTED -> RetirementKind.gifted
        DomainRetirementKind.BROKEN -> RetirementKind.broken
        DomainRetirementKind.LOST -> RetirementKind.lost
        DomainRetirementKind.STOLEN -> RetirementKind.stolen
        DomainRetirementKind.WORN_OUT -> RetirementKind.wornOut
        DomainRetirementKind.OTHER -> RetirementKind.other
    }

    fun mapApiRetirementKind2Domain(kind: RetirementKind?): DomainRetirementKind? = when (kind) {
        null -> null
        RetirementKind.scrapped -> DomainRetirementKind.SCRAPPED
        RetirementKind.sold -> DomainRetirementKind.SOLD
        RetirementKind.gifted -> DomainRetirementKind.GIFTED
        RetirementKind.broken -> DomainRetirementKind.BROKEN
        RetirementKind.lost -> DomainRetirementKind.LOST
        RetirementKind.stolen -> DomainRetirementKind.STOLEN
        RetirementKind.wornOut -> DomainRetirementKind.WORN_OUT
        RetirementKind.other -> DomainRetirementKind.OTHER
    }
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
        ascent = rest.ascent,
        descent = rest.descent,
        powerTotal = rest.powerTotal,
        bike = DomainBike(
            id = rest.bike.id,
            name = rest.bike.name,
            model = rest.bike.model,
            manufacturer = rest.bike.manufacturer,
            purchaseDate = rest.bike.purchaseDate,
            price = rest.bike.price,
            priceCurrency = rest.bike.priceCurrency,
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
