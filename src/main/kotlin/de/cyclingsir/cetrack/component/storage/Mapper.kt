package de.cyclingsir.cetrack.component.storage

import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.component.domain.DomainRetirementKind

/**
 * Manual mapping: retirementKind is a lowercase varchar in the schema
 * (CHECK ('scrapped','sold')) but an enum in the domain.
 */
class ComponentDomain2StorageMapper {

    fun map(domain: DomainComponent): ComponentEntity = ComponentEntity(
        id = domain.id,
        componentTypeId = domain.componentTypeId,
        label = domain.label,
        manufacturer = domain.manufacturer,
        model = domain.model,
        serialNumber = domain.serialNumber,
        vendor = domain.vendor,
        purchaseDate = domain.purchaseDate,
        price = domain.price,
        priceCurrency = domain.priceCurrency,
        retiredAt = domain.retiredAt,
        retirementKind = domain.retirementKind?.name?.lowercase(),
        createdAt = domain.createdAt
    )

    fun map(jpa: ComponentEntity): DomainComponent = DomainComponent(
        id = jpa.id,
        componentTypeId = jpa.componentTypeId,
        label = jpa.label,
        manufacturer = jpa.manufacturer,
        model = jpa.model,
        serialNumber = jpa.serialNumber,
        vendor = jpa.vendor,
        purchaseDate = jpa.purchaseDate,
        price = jpa.price,
        priceCurrency = jpa.priceCurrency,
        retiredAt = jpa.retiredAt,
        retirementKind = jpa.retirementKind?.let { DomainRetirementKind.valueOf(it.uppercase()) },
        createdAt = jpa.createdAt
    )
}
