package de.cyclingsir.cetrack.component.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Assembly tables are queried natively (no assembly entities until CE-0086);
 * the mounting checks stay native for symmetry and index use.
 */
@Repository
interface ComponentRepository : JpaRepository<ComponentEntity, UUID> {

    fun findAllByComponentTypeId(componentTypeId: UUID): List<ComponentEntity>

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM mounting m WHERE m.component_id = :id AND m.dismounted_at IS NULL)"
    )
    fun hasActiveMounting(@Param("id") id: UUID): Boolean

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM assembly_membership am WHERE am.component_id = :id AND am.member_to IS NULL)"
    )
    fun hasActiveMembership(@Param("id") id: UUID): Boolean

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM mounting m WHERE m.component_id = :id)"
    )
    fun wasEverMounted(@Param("id") id: UUID): Boolean

    @Query(
        nativeQuery = true,
        value = "SELECT EXISTS(SELECT 1 FROM assembly_membership am WHERE am.component_id = :id)"
    )
    fun wasEverMember(@Param("id") id: UUID): Boolean

    @Query(
        nativeQuery = true,
        value = "SELECT DISTINCT m.component_id FROM mounting m WHERE m.dismounted_at IS NULL"
    )
    fun activelyMountedComponentIds(): List<UUID>

    @Query(
        nativeQuery = true,
        value = "SELECT DISTINCT am.component_id FROM assembly_membership am WHERE am.member_to IS NULL"
    )
    fun activeMemberComponentIds(): List<UUID>
}
