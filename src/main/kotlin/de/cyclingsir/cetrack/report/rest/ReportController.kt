package de.cyclingsir.cetrack.report.rest

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.infrastructure.api.model.MileageItem
import de.cyclingsir.cetrack.infrastructure.api.model.TourReport
import de.cyclingsir.cetrack.infrastructure.api.model.TourReportBike
import de.cyclingsir.cetrack.infrastructure.api.model.TourReportBucket
import de.cyclingsir.cetrack.infrastructure.api.model.TourReportItem
import de.cyclingsir.cetrack.infrastructure.api.rest.ReportsApi
import de.cyclingsir.cetrack.report.domain.DomainMileageItem
import de.cyclingsir.cetrack.report.domain.DomainTourBucket
import de.cyclingsir.cetrack.report.domain.DomainTourBucketItem
import de.cyclingsir.cetrack.report.domain.DomainTourReport
import de.cyclingsir.cetrack.report.domain.DomainTourReportBike
import de.cyclingsir.cetrack.report.domain.MileageScope
import de.cyclingsir.cetrack.report.domain.ReportService
import de.cyclingsir.cetrack.report.domain.TourGranularity
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

@RestController
class ReportController(private val service: ReportService) : ReportsApi {

    override fun getMileageReport(
        @Valid @RequestParam(value = "scope", required = false, defaultValue = "components") scope: String,
        @Valid @RequestParam(value = "componentId", required = false) componentId: UUID?,
        @Valid @RequestParam(value = "bikeId", required = false) bikeId: UUID?,
        @Valid @RequestParam(value = "from", required = false) from: OffsetDateTime?,
        @Valid @RequestParam(value = "to", required = false) to: OffsetDateTime?
    ): ResponseEntity<List<MileageItem>> {
        val mileageScope = when (scope) {
            "components" -> MileageScope.COMPONENTS
            "bikes" -> MileageScope.BIKES
            else -> throw ServiceException(ErrorCodesDomain.REPORT_SCOPE_INVALID, "got '$scope'")
        }
        val items = service.mileage(mileageScope, componentId, bikeId, from?.toInstant(), to?.toInstant())
        return ResponseEntity.ok(items.map(::toApi))
    }

    override fun getTourReport(
        @RequestParam(value = "granularity", required = false, defaultValue = "month") granularity: String,
        @RequestParam(value = "endYear", required = false) endYear: Short?,
        @RequestParam(value = "yearsBack", required = false, defaultValue = "1") yearsBack: Int
    ): ResponseEntity<TourReport> {
        val tourGranularity = when (granularity) {
            "month" -> TourGranularity.MONTH
            "year" -> TourGranularity.YEAR
            else -> throw ServiceException(ErrorCodesDomain.REPORT_GRANULARITY_INVALID, "got '$granularity'")
        }
        val report = service.tourReport(tourGranularity, endYear?.toInt(), yearsBack)
        return ResponseEntity.ok(toApi(report))
    }

    private fun toApi(domain: DomainTourReport) = TourReport(
        availableYears = domain.availableYears.map { it.toShort() }.toSet(),
        bikes = domain.bikes.map(::toApi),
        buckets = domain.buckets.map(::toApi)
    )

    private fun toApi(domain: DomainTourReportBike) = TourReportBike(
        bikeId = domain.bikeId,
        bikeName = domain.name,
        bikeModel = domain.model
    )

    private fun toApi(domain: DomainTourBucket) = TourReportBucket(
        year = domain.year.toShort(),
        month = domain.month?.toShort(),
        items = domain.items.map(::toApi)
    )

    private fun toApi(domain: DomainTourBucketItem) = TourReportItem(
        bikeId = domain.bikeId,
        distance = domain.distance,
        ascent = domain.ascent,
        durationMoving = domain.durationMoving
    )

    private fun toApi(domain: DomainMileageItem) = MileageItem(
        componentId = domain.componentId,
        label = domain.label,
        manufacturer = domain.manufacturer,
        model = domain.model,
        serialNumber = domain.serialNumber,
        bikeId = domain.bikeId,
        bikeName = domain.bikeName,
        bikeModel = domain.bikeModel,
        distance = domain.distance,
        durationMoving = domain.durationMoving,
        ascent = domain.ascent,
        descent = domain.descent,
        powerTotal = domain.powerTotal
    )
}
