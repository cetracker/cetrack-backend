package de.cyclingsir.cetrack.bike.rest

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.infrastructure.api.model.Bike
import de.cyclingsir.cetrack.infrastructure.api.rest.BikesApi
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
class BikeController(private val service: BikeService, private val mapper: BikeDomain2ApiMapper) : BikesApi {

    override fun createBike(@Valid @RequestBody bike: Bike): ResponseEntity<Bike> {
        logger.debug{ "Add bike bought at ${bike.boughtAt}" }
        val addedBike = service.addBike(mapper.map(bike))
        return ResponseEntity.ok(/* body = */ mapper.map(addedBike))
    }

    override fun getBike(@PathVariable("bikeId") bikeId: UUID): ResponseEntity<Bike> {
        val domainBike = service.getBike(bikeId)
        return ResponseEntity.ok(/* body = */ mapper.map(domainBike))
    }

    override fun modifyBike(@PathVariable("bikeId") bikeId: UUID, @Valid @RequestBody bike: Bike): ResponseEntity<Bike> {
        val domainBike = mapper.map(bike)
        val persistedBike = service.modifyBike(bikeId, domainBike)
        persistedBike?.apply {
            return ResponseEntity.ok(mapper.map(this))
        }
        return ResponseEntity.notFound().build()

    }

    override fun deleteBike(@PathVariable("bikeId") bikeId: UUID): ResponseEntity<Unit> {
        service.deleteBike(bikeId)
        return ResponseEntity(HttpStatus.OK)
    }

    override fun getBikes(): ResponseEntity<List<Bike>> {
        return ResponseEntity.ok(/* body = */ service.getBikes().map(mapper::map))
    }
}
