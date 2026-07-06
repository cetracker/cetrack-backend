package de.cyclingsir.cetrack.bike.storage

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
 * Initially created on 2/1/23.
 */
@Entity(name = "bike")
@Table(name = "bike")
@EntityListeners(AuditingEntityListener::class)
class BikeEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var name: String? = null,

    var model: String? = null,

    var manufacturer: String? = null,

    var purchaseDate: LocalDate? = null,

    var price: String? = null,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    var priceCurrency: String? = null,

    var retiredAt: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)
