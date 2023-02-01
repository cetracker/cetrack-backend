package de.cyclingsir.cetrack.part.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
@Entity(name = "part_type")
@Table(name = "part_type")
@EntityListeners(AuditingEntityListener::class)
class PartTypeEntity(
    @Id var id: UUID,

    @Column(length = 255)
    var name: @NotNull String,

    @CreatedDate
    var createdAt: Instant? = null
)
