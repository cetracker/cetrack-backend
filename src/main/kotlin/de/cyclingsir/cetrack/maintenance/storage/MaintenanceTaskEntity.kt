package de.cyclingsir.cetrack.maintenance.storage

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
 * A recurring maintenance need on one bike (CUET core, CE-0088). bikeId is a
 * plain UUID column - cross-aggregate references are wire (and storage) != domain.
 */
@Entity(name = "maintenance_task")
@Table(name = "maintenance_task")
@EntityListeners(AuditingEntityListener::class)
class MaintenanceTaskEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var bikeId: UUID,

    @Column(columnDefinition = "text")
    var name: String,

    var distanceInterval: Long? = null,

    var timeInterval: Long? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
