package de.cyclingsir.cetrack.assembly.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Joined read projection of one slot with the member active at a given time
 * (mirrors MountingWithPlace) - one query per assembly, no per-slot lookups.
 */
interface AssemblySlotWithMember {
    val slotId: UUID
    val name: String
    val componentTypeId: UUID
    val validFrom: Instant
    val validTo: Instant?
    val createdAt: Instant?
    val memberComponentId: UUID?
    val memberFrom: Instant?
}

@Repository
interface AssemblyRepository : JpaRepository<ComponentAssemblyEntity, UUID> {

    @Query(
        nativeQuery = true,
        value = """SELECT EXISTS(SELECT 1 FROM assembly_membership am
                    JOIN assembly_slot s ON s.id = am.assembly_slot_id
                    WHERE s.assembly_id = :assemblyId)"""
    )
    fun hasMembershipHistory(@Param("assemblyId") assemblyId: UUID): Boolean
}

@Repository
interface AssemblySlotRepository : JpaRepository<AssemblySlotEntity, UUID> {

    fun findAllByAssemblyId(assemblyId: UUID): List<AssemblySlotEntity>

    fun findByIdAndAssemblyId(id: UUID, assemblyId: UUID): AssemblySlotEntity?

    @Query(
        nativeQuery = true,
        value = """SELECT s.id AS slotId, s.name AS name, s.component_type_id AS componentTypeId,
                          s.valid_from AS validFrom, s.valid_to AS validTo, s.created_at AS createdAt,
                          am.component_id AS memberComponentId, am.member_from AS memberFrom
                   FROM assembly_slot s
                   LEFT JOIN assembly_membership am ON am.assembly_slot_id = s.id
                        AND am.member_from <= CAST(:at AS timestamptz)
                        AND (am.member_to IS NULL OR am.member_to > CAST(:at AS timestamptz))
                   WHERE s.assembly_id = :assemblyId
                     AND s.valid_from <= CAST(:at AS timestamptz)
                     AND (s.valid_to IS NULL OR s.valid_to > CAST(:at AS timestamptz))
                   ORDER BY s.name"""
    )
    fun findActiveWithMemberAtTime(
        @Param("assemblyId") assemblyId: UUID,
        @Param("at") at: Instant,
    ): List<AssemblySlotWithMember>

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM assembly_membership WHERE assembly_slot_id = :slotId)"
    )
    fun hasMembershipHistory(@Param("slotId") slotId: UUID): Boolean

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM slot_mapping WHERE assembly_slot_id = :slotId)"
    )
    fun hasSlotMappings(@Param("slotId") slotId: UUID): Boolean
}

@Repository
interface AssemblyMembershipRepository : JpaRepository<AssemblyMembershipEntity, UUID> {

    fun findByComponentIdAndMemberToIsNull(componentId: UUID): AssemblyMembershipEntity?

    fun findByAssemblySlotIdAndMemberToIsNull(assemblySlotId: UUID): AssemblyMembershipEntity?
}

@Repository
interface AssemblyMountingRepository : JpaRepository<AssemblyMountingEntity, UUID> {

    fun findByAssemblyIdAndDismountedAtIsNull(assemblyId: UUID): AssemblyMountingEntity?

    fun findAllByAssemblyIdOrderByMountedAt(assemblyId: UUID): List<AssemblyMountingEntity>

    fun existsByAssemblyId(assemblyId: UUID): Boolean
}
