package de.cyclingsir.cetrack.part.storage

/**
 * Initially created on 2/3/23.
 */
/*
interface ReportProjection {
    var partName: String
    var partType: String
    var validFrom: Instant
    var validUntil: Instant?
}
*/

interface ReportProjectionComplete {
    var partName: String
    var meterTotal: Int
    var secondsTotal: Long
    var altUpTotal: Int
    var altDownTotal: Int
    var powerTotal: Long
}
