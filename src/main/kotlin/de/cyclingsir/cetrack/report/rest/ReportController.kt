package de.cyclingsir.cetrack.report.rest

import de.cyclingsir.cetrack.infrastructure.api.model.MileageItem
import de.cyclingsir.cetrack.infrastructure.api.rest.ReportsApi
import de.cyclingsir.cetrack.report.domain.DomainMileageItem
import de.cyclingsir.cetrack.report.domain.MileageScope
import de.cyclingsir.cetrack.report.domain.ReportService
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
        val mileageScope = if (scope == "bikes") MileageScope.BIKES else MileageScope.COMPONENTS
        val items = service.mileage(mileageScope, componentId, bikeId, from?.toInstant(), to?.toInstant())
        return ResponseEntity.ok(items.map(::toApi))
    }

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
