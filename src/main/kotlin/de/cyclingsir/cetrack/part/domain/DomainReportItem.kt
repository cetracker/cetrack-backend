package de.cyclingsir.cetrack.part.domain

/**
 * Initially created on 3/18/23.
 */
data class DomainReportItem(
    val part: String,
    val distance: Long,
    val durationMoving: Long
    val totalPower: Long,
)
