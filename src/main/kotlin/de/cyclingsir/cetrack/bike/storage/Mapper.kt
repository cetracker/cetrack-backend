package de.cyclingsir.cetrack.bike.storage

import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.common.domain.DomainRetirementKind

/**
 * Manual mapping (CE-0126): retirementKind is a lowercase varchar in the
 * schema (CHECK) but an enum in the domain - kmapper can't auto-convert
 * enum<->String, so this is hand-written like component/storage/Mapper.kt.
 */
class BikeDomain2StorageMapper {

    fun map(domain: DomainBike): BikeEntity = BikeEntity(
        id = domain.id,
        name = domain.name,
        model = domain.model,
        manufacturer = domain.manufacturer,
        purchaseDate = domain.purchaseDate,
        price = domain.price,
        priceCurrency = domain.priceCurrency,
        retiredAt = domain.retiredAt,
        retirementKind = domain.retirementKind?.name?.lowercase(),
        retirementNote = domain.retirementNote,
        createdAt = domain.createdAt
    )

    fun map(jpa: BikeEntity): DomainBike = DomainBike(
        id = jpa.id,
        name = jpa.name,
        model = jpa.model,
        manufacturer = jpa.manufacturer,
        purchaseDate = jpa.purchaseDate,
        price = jpa.price,
        priceCurrency = jpa.priceCurrency,
        retiredAt = jpa.retiredAt,
        retirementKind = jpa.retirementKind?.let { DomainRetirementKind.valueOf(it.uppercase()) },
        retirementNote = jpa.retirementNote,
        createdAt = jpa.createdAt
    )
}
