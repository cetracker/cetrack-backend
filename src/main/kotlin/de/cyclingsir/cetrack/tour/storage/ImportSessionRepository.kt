package de.cyclingsir.cetrack.tour.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ImportSessionRepository : JpaRepository<ImportSessionEntity, UUID> {
    fun findAllByStatus(status: String): List<ImportSessionEntity>
    fun findFirstByStatus(status: String): ImportSessionEntity?
}
