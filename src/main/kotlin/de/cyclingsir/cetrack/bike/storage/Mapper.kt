package de.cyclingsir.cetrack.bike.storage

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.bike.domain.DomainBike

/**
 * Initially created on 1/24/23.
 */

@Mapper
interface BikeDomain2StorageMapper {
    fun map(domain: DomainBike) : BikeEntity

    fun map(jpa: BikeEntity) : DomainBike
}
