package de.cyclingsir.cetrack.maintenance.storage

import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/** One performed occurrence of a MaintenanceTask (CE-0088). */
@Entity(name = "maintenance_event")
@Table(name = "maintenance_event")
@EntityListeners(AuditingEntityListener::class)
class MaintenanceEventEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var maintenanceTaskId: UUID,

    var performedAt: Instant,

    @CreatedDate
    var createdAt: Instant? = null
)
