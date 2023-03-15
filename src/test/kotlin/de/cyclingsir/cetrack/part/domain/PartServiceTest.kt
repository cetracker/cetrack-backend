package de.cyclingsir.cetrack.part.domain;

import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationRepository
import de.cyclingsir.cetrack.part.storage.PartRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

/**
 * Initially created on 3/15/23.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockKExtension::class)
class PartServiceTest {

  companion object {
    val UUID_PART_A = UUID.randomUUID()
    val UUID_PART_B = UUID.randomUUID()
    val UUID_PART_C = UUID.randomUUID()
    val UUID_PART_TYPE_CRANK = UUID.randomUUID()
    val UUID_PART_TYPE_FRONT_TIRE = UUID.randomUUID()
    val UUID_PART_TYPE_REAR_TIRE = UUID.randomUUID()
  }

  @MockK
  private lateinit var partRepository: PartRepository

  @MockK
  private lateinit var relationRepository: PartPartTypeRelationRepository

  private val partDomain2StorageMapper = PartDomain2StorageMapperImpl()
  private val partPartTypeRelationMapper = PartPartTypeRelationDomain2StorageMapperImpl()

  private lateinit var partService: PartService

  @BeforeAll
  fun init() {
//  MockKAnnotations.init(this)
    partService =
      PartService(partRepository, relationRepository, partDomain2StorageMapper, partPartTypeRelationMapper)
  }

  @Test
  fun `when no relation is defined then a new relation is persisted`() {
    val relation =
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, OffsetDateTime.now(), null, null)

    val relationEntity = partPartTypeRelationMapper.map(relation)

    every { relationRepository.save(any()) } returns relationEntity
    every { relationRepository.countByPartId(UUID_PART_A) } returns 0
    every { partRepository.findById(UUID_PART_A) } returns Optional.of(PartEntity(UUID_PART_A, "A"))

    val addedRelation = partService.createPartPartTypeRelation(relation)

    verify(exactly = 1) { relationRepository.save(any()) }
    Assertions.assertEquals(UUID_PART_A, addedRelation.id)
  }

  @Test
  fun `date time instance`() {
    val validFrom = OffsetDateTime.parse("2022-03-01T13:10:23+01")
    val validUntilExpected = OffsetDateTime.parse("2022-02-28T23:59:59+01")
    val validUntilExpectedUTC = OffsetDateTime.parse("2022-02-28T22:59:59Z")

    val validUntilCalculated = validFrom.truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS)

    Assertions.assertEquals(validUntilExpected, validUntilCalculated)
    Assertions.assertEquals(validUntilExpectedUTC, validUntilCalculated.toInstant().atOffset(ZoneOffset.UTC))

  }

  @Test
  fun `when open ended relation B to Crank exists then A to Crank then B to Crank is terminated `() {
    OffsetDateTime.parse("2022-03-01T13:10:23+01")
    val validFrom = OffsetDateTime.parse("2022-03-01T13:10:23+01")
    val expectedValidUntil = validFrom.truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS).toInstant()
    val relationA =
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, validFrom.minus(10, ChronoUnit.DAYS), null, null)
    val relationEntityA = partPartTypeRelationMapper.map(relationA)
    val relationB =
      DomainPartPartTypeRelation(UUID_PART_B, UUID_PART_TYPE_CRANK, validFrom, null, null)
    val relationEntityB = partPartTypeRelationMapper.map(relationB)
    val partEntityB = PartEntity(UUID_PART_B, "B")

    val savedEntitySlot = slot<PartPartTypeRelationEntity>()
    val savedEntities : MutableList<PartPartTypeRelationEntity> = mutableListOf();

    every { relationRepository.countByPartId(UUID_PART_A) } returns 1
    every { relationRepository.countByPartId(UUID_PART_B) } returns 0
//    every { relationRepository.countByPartIdAndValidUntilIsNull(UUID_PART_A) } returns 1
    every { relationRepository.countByPartIdAndValidUntilIsNull(UUID_PART_B) } returns 0
    every { relationRepository.countByPartIdAndPartTypeIdAndValidUntilIsNull(UUID_PART_B, UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 1
    every { relationRepository.findFirstByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns relationEntityA
    every { relationRepository.save(capture(savedEntitySlot)) } answers {
      val entity = savedEntitySlot.captured
      savedEntities.add(entity)
      entity
    }
    every { partRepository.findById(UUID_PART_B) } returns Optional.of(partEntityB)

    val addedRelation = partService.createPartPartTypeRelation(relationB)

    verify (exactly = 2) { relationRepository.save(ofType(PartPartTypeRelationEntity::class)) }
    verify { relationRepository.save(and( match { it.partId == UUID_PART_A  }, match { it.validUntil != null } )) }
    val persistedEntityPartA = savedEntities.find { entity -> entity.partId == UUID_PART_A }
    Assertions.assertEquals(expectedValidUntil, persistedEntityPartA?.validUntil)

  }

}

/**
 * Helper function to mock add createdAt if null
 */
private fun mockSavePart(partEntity: PartEntity) : PartEntity {
  with(partEntity) {
    var creationDate = createdAt ?: Instant.now()
    return PartEntity(id, name, partTypeRelations, boughtAt, creationDate)
  }
}
