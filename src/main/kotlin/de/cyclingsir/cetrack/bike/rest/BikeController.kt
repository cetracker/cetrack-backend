package de.cyclingsir.cetrack.bike.rest

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.infrastructure.api.model.Bike
import de.cyclingsir.cetrack.infrastructure.api.model.BikeInput
import de.cyclingsir.cetrack.infrastructure.api.rest.BikesApi
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
class BikeController(private val service: BikeService, private val mapper: BikeDomain2ApiMapper) : BikesApi {

    override fun createBike(@Valid @RequestBody bikeInput: BikeInput): ResponseEntity<Bike> {
        logger.debug{ "Add bike ${bikeInput.name ?: bikeInput.model}" }
        val addedBike = service.addBike(mapper.map(bikeInput))
        return ResponseEntity
            .created(URI.create("/api/bikes/${addedBike.id}"))
            .body(mapper.map(addedBike))
    }

    override fun getBike(@PathVariable("bikeId") bikeId: UUID): ResponseEntity<Bike> {
        val domainBike = service.getBike(bikeId)
        return ResponseEntity.ok(/* body = */ mapper.map(domainBike))
    }

    override fun modifyBike(@PathVariable("bikeId") bikeId: UUID, @Valid @RequestBody bikeInput: BikeInput): ResponseEntity<Bike> {
        val domainBike = mapper.map(bikeInput)
        val persistedBike = service.modifyBike(bikeId, domainBike)
        return ResponseEntity.ok(mapper.map(persistedBike))
    }

    override fun deleteBike(@PathVariable("bikeId") bikeId: UUID): ResponseEntity<Unit> {
        service.deleteBike(bikeId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun getBikes(): ResponseEntity<List<Bike>> {
        return ResponseEntity.ok(/* body = */ service.getBikes().map(mapper::map))
    }
}
