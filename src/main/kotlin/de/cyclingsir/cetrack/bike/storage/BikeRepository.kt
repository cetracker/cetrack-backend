package de.cyclingsir.cetrack.bike.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
@Repository
interface BikeRepository : JpaRepository<BikeEntity, UUID> {
}
