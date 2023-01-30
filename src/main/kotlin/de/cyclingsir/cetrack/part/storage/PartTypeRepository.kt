package de.cyclingsir.cetrack.part.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
@Repository
interface PartTypeRepository : JpaRepository<PartTypeEntity, UUID> {
}
