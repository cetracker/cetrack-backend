package de.cyclingsir.cetrack.infrastructure.api.model

import java.util.UUID

/**
 *
 * @param mtTourId id for the tour created by MT
 * @param title tour's model name
 * @param distance tour's distance in meters
 * @param durationMoving time cycled in seconds
 * @param timeElapsedDevice time including stops in seconds
 * @param timeRecordedDevice recorded movement by device
 * @param startTimestamp tour's start time in unix epoch
 */
@Suppress("kotlin:S117")
data class DomainMTTour(

    val MTTOURID: String,

    val TITLE: String,

    val DISTANCE: Int,

    val DURATIONMOVING: Long,

    val TIMEELAPSEDDEVICE: Long? = null,

    val TIMERECORDEDDEVICE: Long? = null,

    val STARTTIMESTAMP: Long,

    val STARTYEAR: Short,

    val STARTMONTH: Short,

    val STARTDAY: Short,

    val TOURALTUP: Int,

    val TOURALTDOWN: Int,

    val POWERTOTAL: Long,

    val bikeId: UUID?
)
