package de.cyclingsir.cetrack.bike.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
@Entity(name = "bike")
@Table(name = "bike")
@EntityListeners(AuditingEntityListener::class)
class BikeEntity(
    @Id var id: UUID,

    @Column(length = 255)
    var model: @NotNull String,

    @Column(length = 255)
    var manufacturer: String? = null,

    var boughtAt: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
