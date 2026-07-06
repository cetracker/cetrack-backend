package de.cyclingsir.cetrack.tour.storage

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.tour.domain.TourSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 2/1/23.
 */
@Entity(name = "tour")
@Table(name = "tour")
@EntityListeners(AuditingEntityListener::class)
class TourEntity(
    @Id
        @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID?,

    @Column(length = 30)
    var mtTourId: String? = null,

    @Column(length = 255)
    var title: @NotNull String,

    var distance: Int,

    var durationMoving: Long,

    @ManyToOne var bike: BikeEntity? = null,

    var startedAt: Instant,

    var startYear: Short,

    var startMonth: Short,

    var startDay: Short,

    var ascent: Int,

    var descent: Int,

    var powerTotal: Long,

    @CreatedDate
    var createdAt: Instant? = null,

    var updatedAt: Instant? = null,

    var durationRecorded: Long = 0L,

    var durationElapsed: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var source: TourSource = TourSource.MYTOURBOOK
)
