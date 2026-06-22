package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.infrastructure.api.model.DomainMTTour
import java.util.UUID

data class DomainImportSession(
    val sessionId: UUID,
    val status: String,
    val dbVersion: Int,
    val hasDrift: Boolean,
    val candidates: List<DomainMTTour>,
    val warnings: List<DomainImportWarning>
)
