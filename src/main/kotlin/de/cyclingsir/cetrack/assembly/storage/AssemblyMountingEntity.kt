package de.cyclingsir.cetrack.assembly.storage

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
 * Temporal fact: the assembly as a unit on a bike over [mountedAt,
 * dismountedAt) (domain-model.md §3). Mounting the assembly creates/adopts
 * one governed Mounting per member; dismounting closes them all.
 */
@Entity(name = "assembly_mounting")
@Table(name = "assembly_mounting")
@EntityListeners(AuditingEntityListener::class)
class AssemblyMountingEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var assemblyId: UUID,

    var bikeId: UUID,

    var mountedAt: Instant,

    var dismountedAt: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
