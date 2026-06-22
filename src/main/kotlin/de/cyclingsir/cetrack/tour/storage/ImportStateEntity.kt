package de.cyclingsir.cetrack.tour.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "import_state")
class ImportStateEntity(
    @Id
    val id: Int,

    @Column(nullable = false)
    var lastDbVersion: Int,

    @Column(nullable = false)
    var updatedAt: Instant
)
