package de.cyclingsir.cetrack.component.rest

import de.cyclingsir.cetrack.component.domain.DomainComponent
import de.cyclingsir.cetrack.component.domain.DomainComponentStatus
import de.cyclingsir.cetrack.component.domain.DomainRetirementKind
import de.cyclingsir.cetrack.infrastructure.api.model.Component
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentInput
import de.cyclingsir.cetrack.infrastructure.api.model.ComponentStatus
import java.time.ZoneOffset

/**
 * Manual mapping: the enums (status, retirementKind) need explicit conversion.
 */
class ComponentDomain2ApiMapper {

    fun map(domain: DomainComponent): Component = Component(
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
        retiredAt = domain.retiredAt?.atOffset(ZoneOffset.UTC),
        retirementKind = domain.retirementKind?.let { Component.RetirementKind.forValue(it.name.lowercase()) },
        status = domain.status?.let(::map),
        directlyMounted = domain.directlyMounted,
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(rest: ComponentInput): DomainComponent = DomainComponent(
        componentTypeId = rest.componentTypeId,
        label = rest.label,
        manufacturer = rest.manufacturer,
        model = rest.model,
        serialNumber = rest.serialNumber,
        vendor = rest.vendor,
        purchaseDate = rest.purchaseDate,
        price = rest.price,
        priceCurrency = rest.priceCurrency
    )

    fun map(status: DomainComponentStatus): ComponentStatus = when (status) {
        DomainComponentStatus.IN_STOCK -> ComponentStatus.inStock
        DomainComponentStatus.IN_ASSEMBLY -> ComponentStatus.inAssembly
        DomainComponentStatus.MOUNTED -> ComponentStatus.mounted
        DomainComponentStatus.RETIRED -> ComponentStatus.retired
    }

    fun map(status: ComponentStatus): DomainComponentStatus = when (status) {
        ComponentStatus.inStock -> DomainComponentStatus.IN_STOCK
        ComponentStatus.inAssembly -> DomainComponentStatus.IN_ASSEMBLY
        ComponentStatus.mounted -> DomainComponentStatus.MOUNTED
        ComponentStatus.retired -> DomainComponentStatus.RETIRED
    }
}
