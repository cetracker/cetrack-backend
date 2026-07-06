package de.cyclingsir.cetrack.catalog.storage

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

@Entity(name = "position")
@Table(name = "position")
@EntityListeners(AuditingEntityListener::class)
class PositionEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    @Column(columnDefinition = "text")
    var name: String,

    @CreatedDate
    var createdAt: Instant? = null
)
