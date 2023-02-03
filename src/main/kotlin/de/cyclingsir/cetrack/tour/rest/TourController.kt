package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.infrastructure.api.model.Tour
import de.cyclingsir.cetrack.infrastructure.api.rest.ToursApi
import de.cyclingsir.cetrack.tour.domain.TourService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
class TourController(private val service: TourService, private val mapper: TourDomain2ApiMapper) : ToursApi {

    override fun createTour(@Valid @RequestBody tour: Tour): ResponseEntity<Tour> {
        logger.debug("Add tour with title ${tour.title}")
        val addedTour = service.addTour(mapper.map(tour))
        return ResponseEntity.ok(/* body = */ mapper.map(addedTour))
    }

    override fun getTour(@PathVariable("tourId") tourId: UUID): ResponseEntity<Tour> {
        val domainTour = service.getTour(tourId)
        return ResponseEntity.ok(/* body = */ mapper.map(domainTour))
    }

    override fun getTours(): ResponseEntity<List<Tour>> {
        val domainTours = service.getTours()
        return ResponseEntity.ok(/* body = */ domainTours.map(mapper::map))
    }
}
