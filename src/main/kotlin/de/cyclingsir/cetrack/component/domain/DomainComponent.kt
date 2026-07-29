package de.cyclingsir.cetrack.component.domain

import de.cyclingsir.cetrack.common.domain.DomainRetirementKind
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Derived lifecycle state (domain-model.md §3): never stored, computed from
 * retiredAt + active Mounting + active AssemblyMembership.
 */
enum class DomainComponentStatus {
    IN_STOCK, IN_ASSEMBLY, MOUNTED, RETIRED
}

data class DomainComponent(
    val id: UUID? = null,
    val componentTypeId: UUID,
    val label: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val vendor: String? = null,
    val purchaseDate: LocalDate? = null,
    val price: String? = null,
    val priceCurrency: String? = null,
    val retiredAt: Instant? = null,
    val retirementKind: DomainRetirementKind? = null,
    val retirementNote: String? = null,
    val status: DomainComponentStatus? = null,
    val directlyMounted: Boolean = false,
    val createdAt: Instant? = null
)
