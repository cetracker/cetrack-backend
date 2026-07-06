package de.cyclingsir.cetrack.bike.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * BikeComposition: a place with a function on one specific bike (CUET §2/§3).
 * Cross-aggregate references (componentType, position) are plain UUID columns.
 */
@Entity(name = "mount_point")
@Table(name = "mount_point")
@EntityListeners(AuditingEntityListener::class)
class MountPointEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var bikeId: UUID,

    var componentTypeId: UUID,

    var positionId: UUID? = null,

    @Column(columnDefinition = "text")
    var name: String,

    var mandatory: Boolean = false,

    @CreatedDate
    var createdAt: Instant? = null
)
