package de.cyclingsir.cetrack.part.domain

import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
@SuppressWarnings("EI_EXPOSE_REP")
data class DomainPart(
    val id: UUID?,
    val label: String?,
    val manufacturer: String?,
    val model: String?,
    val serialNumber: String?,
    val vendor: String?,
    val purchasePrice: String?,
    val purchasePriceCurrency: String?,
    val firstUsedDate: Instant?,
    val boughtAt: Instant?,
    val retiredAt: Instant?,
    val partTypeRelations: List<DomainPartPartTypeRelation>?,
    val createdAt: Instant?
)
