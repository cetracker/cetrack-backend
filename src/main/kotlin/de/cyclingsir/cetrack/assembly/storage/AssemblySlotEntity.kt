package de.cyclingsir.cetrack.assembly.storage

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
 * A slot within an assembly accepting exactly one ComponentType, valid over
 * [validFrom, validTo) - slots may be added/removed over the assembly's life
 * (domain-model.md §3).
 */
@Entity(name = "assembly_slot")
@Table(name = "assembly_slot")
@EntityListeners(AuditingEntityListener::class)
class AssemblySlotEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var assemblyId: UUID,

    var componentTypeId: UUID,

    @Column(columnDefinition = "text")
    var name: String,

    var validFrom: Instant,

    var validTo: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
