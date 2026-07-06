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
 * Temporal fact: a Component occupies one AssemblySlot over [memberFrom,
 * memberTo) (domain-model.md §3). memberTo null = currently a member. The DB
 * exclusion constraints (V1.0 per-component, V1.1 per-slot) are the
 * concurrent-safety net for the <=1-active invariants.
 */
@Entity(name = "assembly_membership")
@Table(name = "assembly_membership")
@EntityListeners(AuditingEntityListener::class)
class AssemblyMembershipEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var componentId: UUID,

    var assemblySlotId: UUID,

    var memberFrom: Instant,

    var memberTo: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
