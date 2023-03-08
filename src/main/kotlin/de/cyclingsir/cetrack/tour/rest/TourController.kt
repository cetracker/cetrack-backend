package de.cyclingsir.cetrack.tour.rest

import de.cyclingsir.cetrack.infrastructure.api.model.MTTour
import de.cyclingsir.cetrack.infrastructure.api.model.Tour
import de.cyclingsir.cetrack.infrastructure.api.rest.ToursApi
import de.cyclingsir.cetrack.tour.domain.TourService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.nio.charset.Charset
import java.util.UUID

    private val logger = KotlinLogging.logger {}

@RestController
class TourController(
    private val service: TourService,
    private val mapper: TourDomain2ApiMapper,
    private val importMapper: MTTourDomain2ApiMapper
) : ToursApi {

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

    override fun relateBikeToTour(
        @Parameter(required = true) @PathVariable("tourId") tourId: java.util.UUID,
        @NotNull @Parameter(required = true) @Valid @RequestParam(value = "bikeId", required = true) bikeId: java.util.UUID
    ): ResponseEntity<Tour> {
        val modifiedTour = service.relateTourToBike(tourId, bikeId)
        return ResponseEntity.ok(mapper.map(modifiedTour))
    }

    override fun uploadTours(@Valid @RequestBody body: String): ResponseEntity<Unit> {

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    override fun importTours(@Valid @RequestBody mtTour: List<MTTour>): ResponseEntity<Unit> {
        service.importTours(mtTour.map(importMapper::map))
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/tours/uploadTourFile")
    fun uploadTourFile(@RequestParam("file") file: MultipartFile, redirectAttributes: RedirectAttributes): String {

        logger.debug { "${file.name} ${file.size} ${file.originalFilename}" }

        val contentString = file.inputStream.readBytes().toString(Charset.defaultCharset())
        logger.info { contentString }


        redirectAttributes.addFlashAttribute("message", "You successfully uploaded ${file.originalFilename} !")

        return "redirect:/"
    }
}
