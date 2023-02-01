package de.cyclingsir.cetrack.bike.rest

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.infrastructure.api.model.Bike

/**
 * Initially created on 2/1/23.
 */
@Mapper
interface BikeDomain2ApiMapper {

    fun map(domain: DomainBike) : Bike

    fun map(rest: Bike) : DomainBike
}
