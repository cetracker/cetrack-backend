package de.cyclingsir.cetrack.part.domain

/**
 * Initially created on 3/18/23.
 */
data class DomainReportItem(
    val label: String?,
    val manufacturer: String?,
    val model: String?,
    val serialNumber: String?,
    val distance: Long,
    val durationMoving: Long,
    val altUp: Long,
    val altDown: Long,
    val totalPower: Long,
)
