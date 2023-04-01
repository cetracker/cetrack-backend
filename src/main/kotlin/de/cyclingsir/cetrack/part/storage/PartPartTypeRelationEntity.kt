package de.cyclingsir.cetrack.part.storage

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/28/23.
 */
@IdClass(CompositeKey::class)
@Entity(name = "part_part_types")
@Table(name = "part_part_types")
class PartPartTypeRelationEntity(
    @Id
    @Column(name = "part_id")
    var partId: UUID,

    @Id
    @Column(name = "part_type_id")
    var partTypeId: UUID,

    @Id
    var validFrom: Instant,

    var validUntil: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("partTypeId")
    var partType: PartTypeEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("partId")
    var part: PartEntity)

@Embeddable
class CompositeKey(
    var partId: UUID,
    var partTypeId: UUID,
    var validFrom: Instant) : Serializable
