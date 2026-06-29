package de.cyclingsir.cetrack.tour.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ImportIgnoreRepository : JpaRepository<ImportIgnoreEntity, UUID> {
    fun existsByStartedAtAndDistanceBetween(startedAt: Instant, distMin: Int, distMax: Int): Boolean
}
