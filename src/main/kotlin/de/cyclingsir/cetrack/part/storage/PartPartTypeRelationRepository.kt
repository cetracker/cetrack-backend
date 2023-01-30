package de.cyclingsir.cetrack.part.storage;

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Initially created on 10/12/2022.
 */
@Repository
interface PartPartTypeRelationRepository : JpaRepository<PartPartTypeRelationEntity, UUID> {

  fun findAllByPartId(partId: UUID): MutableList<PartPartTypeRelationEntity>

  fun findAllByPartIdAndPartTypeId(partId: UUID, partTypeId: UUID): MutableList<PartPartTypeRelationEntity>

  fun findFirstByPartIdAndValidUntilIsNull(partId: UUID): PartPartTypeRelationEntity?

  fun findFirstByPartIdAndValidUntilIsNotNullOrderByValidUntilDesc(partId: UUID): PartPartTypeRelationEntity?

  fun countByPartId(partId: UUID): Long

  fun countByPartIdAndPartTypeIdAndValidUntilIsNull(partId: UUID, partTypeId: UUID): Long

  fun countByPartIdAndValidUntilIsNull(partId: UUID): Long
}
