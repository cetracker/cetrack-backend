package de.cyclingsir.cetrack.tour.storage

import com.syouth.kmapper.processor_annotations.Mapper
import de.cyclingsir.cetrack.common.domain.DomainRetirementKind
import de.cyclingsir.cetrack.tour.domain.DomainTour
import java.util.UUID

/**
 * Initially created on 1/24/23.
 */
interface TourDomain2StorageMapperSupport {
    fun mapNullableUUIDToUUID(i: UUID?): UUID = i ?: UUID.randomUUID()!!
}

/**
 * Deliberately NOT extending TourDomain2StorageMapperSupport (CE-0126): doing
 * so makes mapNullableUUIDToUUID visible to kmapper for TourEntity.id/nested
 * BikeEntity.id too, which pre-assigns a UUID for new (null-id) rows and
 * breaks Hibernate's new-vs-existing detection on @GeneratedValue(UUID)
 * (StaleObjectStateException on insert). Declared directly on the mapper
 * instead so only the two enum converters below are exposed.
 */
@Mapper
interface TourDomain2StorageMapper {
    fun map(domain: DomainTour) : TourEntity

    fun map(jpa: TourEntity) : DomainTour

    /** Nested in DomainTour.bike/TourEntity.bike; storage is a lowercase varchar (CE-0126). */
    fun mapDomainRetirementKind2Storage(kind: DomainRetirementKind?): String? = kind?.name?.lowercase()
    fun mapStorageRetirementKind2Domain(kind: String?): DomainRetirementKind? =
        kind?.let { DomainRetirementKind.valueOf(it.uppercase()) }
}
