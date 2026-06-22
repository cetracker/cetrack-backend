package de.cyclingsir.cetrack.tour.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "import_session")
class ImportSessionEntity(
    @Id
    val id: UUID,

    @Column(length = 20, nullable = false)
    var status: String,

    @Column(nullable = false)
    val dbVersion: Int,

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    val payload: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
)
