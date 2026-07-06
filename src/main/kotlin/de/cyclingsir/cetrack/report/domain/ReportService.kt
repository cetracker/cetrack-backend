package de.cyclingsir.cetrack.report.domain

import de.cyclingsir.cetrack.report.storage.MileageQueryDao
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ReportService(private val dao: MileageQueryDao) {

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
}
