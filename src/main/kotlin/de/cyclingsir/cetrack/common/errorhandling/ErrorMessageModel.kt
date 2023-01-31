package de.cyclingsir.cetrack.common.errorhandling

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Initially created on 1/31/23.
 */
class ErrorDetails(
    var code: Int,
    var exceptionName: String,
    var message: String,
    var path: String,
    var status: Int,
    var timeStamp: OffsetDateTime? = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
)
