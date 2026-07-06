package de.cyclingsir.cetrack.mounting.storage

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
 * Temporal fact: one Component occupies one MountPoint over [mountedAt,
 * dismountedAt) (domain-model.md §3). dismountedAt null = currently mounted;
 * assemblyMountingId = provenance (governed by an assembly mounting, CE-0086).
 * Cross-aggregate references are plain UUID columns - the DB exclusion
 * constraints are the concurrent-safety net for the <=1-active invariants.
 */
@Entity(name = "mounting")
@Table(name = "mounting")
@EntityListeners(AuditingEntityListener::class)
class MountingEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var componentId: UUID,

    var mountPointId: UUID,

    var assemblyMountingId: UUID? = null,

    var mountedAt: Instant,

    var dismountedAt: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
