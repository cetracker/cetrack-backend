package de.cyclingsir.cetrack.component.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One physical, individually tracked component (CUET core, CE-0083).
 * Cross-aggregate references are plain UUID columns - wire (and storage) != domain.
 */
@Entity(name = "component")
@Table(name = "component")
@EntityListeners(AuditingEntityListener::class)
class ComponentEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var componentTypeId: UUID,

    @Column(columnDefinition = "text")
    var label: String,

    @Column(columnDefinition = "text")
    var manufacturer: String? = null,

    @Column(columnDefinition = "text")
    var model: String? = null,

    @Column(columnDefinition = "text")
    var serialNumber: String? = null,

    @Column(columnDefinition = "text")
    var vendor: String? = null,

    var purchaseDate: LocalDate? = null,

    var price: String? = null,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    var priceCurrency: String? = null,

    var retiredAt: Instant? = null,

    @Column(length = 20)
    var retirementKind: String? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
