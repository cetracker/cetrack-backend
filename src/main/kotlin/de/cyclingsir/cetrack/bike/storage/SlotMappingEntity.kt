package de.cyclingsir.cetrack.bike.storage

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
 * Remembered user resolution of an ambiguous assembly slot on this bike
 * (ADR-0003). Written only by the assembly mount flow (CE-0086); CE-0083
 * exposes read + reset. assemblySlotId stays a raw UUID - no assembly
 * aggregate exists yet.
 */
@Entity(name = "slot_mapping")
@Table(name = "slot_mapping")
@EntityListeners(AuditingEntityListener::class)
class SlotMappingEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    var assemblySlotId: UUID,

    var bikeId: UUID,

    var mountPointId: UUID,

    @CreatedDate
    var createdAt: Instant? = null
)
