package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
@Entity(name = "tour")
@Table(name = "tour")
@EntityListeners(AuditingEntityListener::class)
class TourEntity(
    @Id var id: UUID,

    @Column(length = 255)
    var title: @NotNull String,

    var length: Int,

    var duration: Duration,

    @ManyToOne var bike: BikeEntity? = null,

    var startedAt: Instant,

    @CreatedDate
    var createdAt: Instant? = null
)
