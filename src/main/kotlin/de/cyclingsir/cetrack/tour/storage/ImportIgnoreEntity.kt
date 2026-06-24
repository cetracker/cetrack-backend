package de.cyclingsir.cetrack.tour.storage

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "import_ignore")
class ImportIgnoreEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    val startedAt: Instant,
    val distance: Int,
    val durationMoving: Long,
    val createdAt: Instant = Instant.now()
)
