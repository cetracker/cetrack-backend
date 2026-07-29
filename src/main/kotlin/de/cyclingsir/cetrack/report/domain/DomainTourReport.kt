package de.cyclingsir.cetrack.report.domain

import java.util.UUID

enum class TourGranularity { MONTH, YEAR }

data class DomainTourReportBike(
    val bikeId: UUID?,
    val name: String?,
    val model: String?
)

data class DomainTourBucketItem(
    val bikeId: UUID?,
    val distance: Long,
    val ascent: Long,
    val durationMoving: Long
)

data class DomainTourBucket(
    val year: Int,
    val month: Int?,
    val items: List<DomainTourBucketItem>
)

data class DomainTourReport(
    val availableYears: List<Int>,
    val bikes: List<DomainTourReportBike>,
    val buckets: List<DomainTourBucket>
)
