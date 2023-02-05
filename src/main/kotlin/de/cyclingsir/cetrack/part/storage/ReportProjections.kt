package de.cyclingsir.cetrack.part.storage

import java.time.Instant

/**
 * Initially created on 2/3/23.
 */
interface ReportProjection {
    var partName: String
    var partType: String
    var validFrom: Instant
    var validUntil: Instant?
}

interface ReportProjectionComplete {
    var partName: String
    var meterTotal: Int
    var secondsTotal: Long
}
