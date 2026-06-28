package de.cyclingsir.cetrack.tour.domain

import java.time.Instant
import java.time.ZoneOffset

fun genMtId(startedAt: Instant, distance: Int): String {
    val t = startedAt.atZone(ZoneOffset.UTC)
    return "${t.year}${t.monthValue}${t.dayOfMonth}${t.hour}${t.minute}$distance"
}
