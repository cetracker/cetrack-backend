package de.cyclingsir.cetrack.part.domain

import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 *
 * @param partId the part's id
 * @param partTypeId the part type's id
 * @param validFrom start date the relation is valid from
 * @param validUntil the last date, the relation is valid
 */
data class DomainPartPartTypeRelation(

    val partId: UUID,
    val partTypeId: UUID,
    val validFrom: Instant,
    val validUntil: Instant?
)
