package de.cyclingsir.cetrack.report.domain

import java.util.UUID

enum class MileageScope { COMPONENTS, BIKES }

data class DomainMileageItem(
    val componentId: UUID? = null,
    val label: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val bikeId: UUID? = null,
    val bikeName: String? = null,
    val bikeModel: String? = null,
    val distance: Long,
    val durationMoving: Long,
    val ascent: Long,
    val descent: Long,
    val powerTotal: Long
)
