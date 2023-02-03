package de.cyclingsir.cetrack.tour.storage

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.tour.domain.DomainTour
import java.util.UUID

/**
 * Initially created on 1/24/23.
 */
interface TourDomain2StorageMapperSupport {
    fun mapNullableUUIDToUUID(i: UUID?): UUID = i ?: UUID.randomUUID()
}

@Mapper
interface TourDomain2StorageMapper : TourDomain2StorageMapperSupport {
    fun map(domain: DomainTour) : TourEntity

    fun map(jpa: TourEntity) : DomainTour
}
