package de.cyclingsir.cetrack.mounting.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Read projection denormalized with the owning bike and mount point name
 * (spec: Mounting DTO resolvability) - single joined query, no per-row lookups.
 */
interface MountingWithPlace {
    val id: UUID
    val componentId: UUID
    val mountPointId: UUID
    val assemblyMountingId: UUID?
    val mountedAt: Instant
    val dismountedAt: Instant?
    val createdAt: Instant?
    val bikeId: UUID
    val mountPointName: String
}

@Repository
interface MountingRepository : JpaRepository<MountingEntity, UUID> {

    fun findByComponentIdAndDismountedAtIsNull(componentId: UUID): MountingEntity?

    fun findByMountPointIdAndDismountedAtIsNull(mountPointId: UUID): MountingEntity?

    @Query(
        """SELECT m.id AS id, m.component_id AS componentId, m.mount_point_id AS mountPointId,
                  m.assembly_mounting_id AS assemblyMountingId, m.mounted_at AS mountedAt,
                  m.dismounted_at AS dismountedAt, m.created_at AS createdAt,
                  mp.bike_id AS bikeId, mp.name AS mountPointName
           FROM mounting m
           JOIN mount_point mp ON mp.id = m.mount_point_id
           WHERE (CAST(:componentId AS uuid) IS NULL OR m.component_id = :componentId)
             AND (CAST(:mountPointId AS uuid) IS NULL OR m.mount_point_id = :mountPointId)
             AND (CAST(:bikeId AS uuid) IS NULL OR mp.bike_id = :bikeId)
             AND (CAST(:activeAt AS timestamptz) IS NULL
                  OR (m.mounted_at <= :activeAt AND (m.dismounted_at IS NULL OR m.dismounted_at > :activeAt)))
           ORDER BY m.mounted_at""",
        nativeQuery = true
    )
    fun findWithPlace(
        @Param("componentId") componentId: UUID?,
        @Param("mountPointId") mountPointId: UUID?,
        @Param("bikeId") bikeId: UUID?,
        @Param("activeAt") activeAt: Instant?,
    ): List<MountingWithPlace>

    @Query(
        """SELECT m.id AS id, m.component_id AS componentId, m.mount_point_id AS mountPointId,
                  m.assembly_mounting_id AS assemblyMountingId, m.mounted_at AS mountedAt,
                  m.dismounted_at AS dismountedAt, m.created_at AS createdAt,
                  mp.bike_id AS bikeId, mp.name AS mountPointName
           FROM mounting m
           JOIN mount_point mp ON mp.id = m.mount_point_id
           WHERE m.id = :id""",
        nativeQuery = true
    )
    fun findWithPlaceById(@Param("id") id: UUID): MountingWithPlace?

    /**
     * ADR-0001 §3 guided-choice predicate, CE-0086-proof: an active membership
     * whose assembly is NOT currently mounted. A member of a mounted assembly
     * is §1 adoption / §2 propagation instead - never a guided choice.
     */
    @Query(
        """SELECT s.assembly_id FROM assembly_membership am
           JOIN assembly_slot s ON s.id = am.assembly_slot_id
           WHERE am.component_id = :componentId AND am.member_to IS NULL
             AND NOT EXISTS (SELECT 1 FROM assembly_mounting asm
                             WHERE asm.assembly_id = s.assembly_id AND asm.dismounted_at IS NULL)""",
        nativeQuery = true
    )
    fun findUnmountedAssemblyOfActiveMembership(@Param("componentId") componentId: UUID): UUID?

    @Query(
        """SELECT EXISTS(SELECT 1 FROM mounting m
           WHERE m.component_id = :componentId AND m.id <> :selfId
             AND tstzrange(m.mounted_at, m.dismounted_at, '[)') && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)'))""",
        nativeQuery = true
    )
    fun overlapsOtherOfComponent(
        @Param("selfId") selfId: UUID,
        @Param("componentId") componentId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant?,
    ): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM mounting m
           WHERE m.mount_point_id = :mountPointId AND m.id <> :selfId
             AND tstzrange(m.mounted_at, m.dismounted_at, '[)') && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)'))""",
        nativeQuery = true
    )
    fun overlapsOtherOfMountPoint(
        @Param("selfId") selfId: UUID,
        @Param("mountPointId") mountPointId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant?,
    ): Boolean
}
