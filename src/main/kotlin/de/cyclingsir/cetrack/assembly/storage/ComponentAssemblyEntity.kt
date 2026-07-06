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
 * ComponentAssembly aggregate root (domain-model.md §3): a named physical
 * group of components mounted to a bike as one unit. Cross-aggregate
 * references (positionId) are plain UUID columns.
 */
@Entity(name = "component_assembly")
@Table(name = "component_assembly")
@EntityListeners(AuditingEntityListener::class)
class ComponentAssemblyEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var positionId: UUID? = null,

    @Column(columnDefinition = "text")
    var name: String,

    @CreatedDate
    var createdAt: Instant? = null
)
