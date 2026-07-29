package de.cyclingsir.cetrack.report.domain

import de.cyclingsir.cetrack.report.storage.MileageQueryDao
import de.cyclingsir.cetrack.report.storage.TourReportQueryDao
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ReportService(
    private val dao: MileageQueryDao,
    private val tourReportDao: TourReportQueryDao
) {

    fun mileage(
        scope: MileageScope,
        componentId: UUID?,
        bikeId: UUID?,
        from: Instant?,
        to: Instant?,
    ): List<DomainMileageItem> = when (scope) {
        MileageScope.COMPONENTS -> dao.perComponent(componentId, bikeId, from, to)
        MileageScope.BIKES -> dao.perBike(componentId, bikeId, from, to)
    }

    fun tourReport(granularity: TourGranularity, endYear: Int?, yearsBack: Int): DomainTourReport {
        val availableYears = tourReportDao.availableYears()
        val resolvedEndYear = endYear ?: availableYears.firstOrNull()
            ?: return DomainTourReport(availableYears = emptyList(), bikes = emptyList(), buckets = emptyList())
        val startYear = resolvedEndYear - yearsBack + 1
        val rows = tourReportDao.tourAggregates(granularity, startYear, resolvedEndYear)

        val bikes = rows
            .distinctBy { it.bikeId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { (it.bikeName ?: it.bikeModel).orEmpty() })
            .sortedBy { it.bikeId == null }
            .map { DomainTourReportBike(bikeId = it.bikeId, name = it.bikeName, model = it.bikeModel) }

        val buckets = rows
            .groupBy { it.year to it.month }
            .toSortedMap(compareBy({ it.first }, { it.second ?: 0 }))
            .map { (key, bucketRows) ->
                DomainTourBucket(
                    year = key.first,
                    month = key.second,
                    items = bucketRows.map {
                        DomainTourBucketItem(
                            bikeId = it.bikeId,
                            distance = it.distance,
                            ascent = it.ascent,
                            durationMoving = it.durationMoving
                        )
                    }
                )
            }

        return DomainTourReport(availableYears = availableYears, bikes = bikes, buckets = buckets)
    }
}
