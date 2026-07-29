package de.cyclingsir.cetrack.bike.rest

import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.common.domain.DomainRetirementKind
import de.cyclingsir.cetrack.infrastructure.api.model.Bike
import de.cyclingsir.cetrack.infrastructure.api.model.BikeInput
import de.cyclingsir.cetrack.infrastructure.api.model.RetirementKind
import java.time.ZoneOffset

/**
 * Initially created on 2/1/23. Manual mapping (CE-0126): kmapper can't
 * auto-convert enum<->enum (RetirementKind wire enum <-> DomainRetirementKind),
 * same reasoning as component/rest/Mapper.kt and bike/storage/Mapper.kt.
 */
class BikeDomain2ApiMapper {

    fun map(domain: DomainBike): Bike = Bike(
        id = domain.id,
        name = domain.name,
        model = domain.model,
        manufacturer = domain.manufacturer,
        purchaseDate = domain.purchaseDate,
        price = domain.price,
        priceCurrency = domain.priceCurrency,
        retiredAt = domain.retiredAt?.atOffset(ZoneOffset.UTC),
        retirementKind = domain.retirementKind?.let(::map),
        retirementNote = domain.retirementNote,
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(rest: Bike): DomainBike = DomainBike(
        id = rest.id,
        name = rest.name,
        model = rest.model,
        manufacturer = rest.manufacturer,
        purchaseDate = rest.purchaseDate,
        price = rest.price,
        priceCurrency = rest.priceCurrency,
        retiredAt = rest.retiredAt?.toInstant(),
        retirementKind = rest.retirementKind?.let(::map),
        retirementNote = rest.retirementNote,
        createdAt = rest.createdAt?.toInstant()
    )

    fun map(rest: BikeInput): DomainBike = DomainBike(
        name = rest.name,
        model = rest.model,
        manufacturer = rest.manufacturer,
        purchaseDate = rest.purchaseDate,
        price = rest.price,
        priceCurrency = rest.priceCurrency
    )

    fun map(kind: DomainRetirementKind): RetirementKind = when (kind) {
        DomainRetirementKind.SCRAPPED -> RetirementKind.scrapped
        DomainRetirementKind.SOLD -> RetirementKind.sold
        DomainRetirementKind.GIFTED -> RetirementKind.gifted
        DomainRetirementKind.BROKEN -> RetirementKind.broken
        DomainRetirementKind.LOST -> RetirementKind.lost
        DomainRetirementKind.STOLEN -> RetirementKind.stolen
        DomainRetirementKind.WORN_OUT -> RetirementKind.wornOut
        DomainRetirementKind.OTHER -> RetirementKind.other
    }

    fun map(kind: RetirementKind): DomainRetirementKind = when (kind) {
        RetirementKind.scrapped -> DomainRetirementKind.SCRAPPED
        RetirementKind.sold -> DomainRetirementKind.SOLD
        RetirementKind.gifted -> DomainRetirementKind.GIFTED
        RetirementKind.broken -> DomainRetirementKind.BROKEN
        RetirementKind.lost -> DomainRetirementKind.LOST
        RetirementKind.stolen -> DomainRetirementKind.STOLEN
        RetirementKind.wornOut -> DomainRetirementKind.WORN_OUT
        RetirementKind.other -> DomainRetirementKind.OTHER
    }
}
