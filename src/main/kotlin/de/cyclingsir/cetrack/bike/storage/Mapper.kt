package de.cyclingsir.cetrack.bike.storage

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.bike.domain.DomainBike
import java.util.UUID

/**
 * Initially created on 1/24/23.
 */
interface BikeDomain2StorageMapperSupport {
    fun mapNullableUUIDToUUID(i: UUID?): UUID = i ?: UUID.randomUUID()
}

@Mapper
interface BikeDomain2StorageMapper : BikeDomain2StorageMapperSupport {
    fun map(domain: DomainBike) : BikeEntity

    fun map(jpa: BikeEntity) : DomainBike
}
