package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.DomainBike
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesService
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationEntity
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationRepository
import de.cyclingsir.cetrack.part.storage.PartRepository
import de.cyclingsir.cetrack.part.storage.PartStorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeDomain2StorageMapperImpl
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

/**
 * Initially created on 3/15/23.
 */
@ExtendWith(MockKExtension::class)
class PartServiceTest {

  companion object {
    val UUID_PART_A = UUID.randomUUID()!!
    val UUID_PART_B = UUID.randomUUID()!!
    val UUID_PART_TYPE_CRANK = UUID.randomUUID()!!
    val UUID_BIKE = UUID.randomUUID()!!
  }

  @MockK
  private lateinit var partRepository: PartRepository

  @MockK
  private lateinit var relationRepository: PartPartTypeRelationRepository

  private var bike =  DomainBike("Bike", "Manufacturer", UUID_BIKE, null, null, null)
  private var domainPartA = DomainPart(
    id = UUID_PART_A, label = "A", manufacturer = null, model = null, serialNumber = null,
    vendor = null, purchasePrice = null, purchasePriceCurrency = null, firstUsedDate = null,
    boughtAt = null, retiredAt = null, partTypeRelations = emptyList(), createdAt = null)
  private var domainPartTypeCrank = DomainPartType(UUID_PART_TYPE_CRANK, "Crank", true, emptyList(), bike, null)

  private val partStorageMapper =
    PartStorageMapper(PartDomain2StorageMapperImpl(), PartTypeDomain2StorageMapperImpl(),
      PartPartTypeRelationDomain2StorageMapperImpl())

  private lateinit var partService: PartService

  @BeforeEach
  fun init() {
    MockKAnnotations.init(this)
    partService = PartService(partRepository, relationRepository, partStorageMapper)
  }

  @Test
  fun `when no relation is defined then a new relation is persisted`() {
    val relation =
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, OffsetDateTime.now(), null, domainPartA, domainPartTypeCrank)

    val relationEntity = partStorageMapper.map(relation)

    val partEntity = PartEntity(UUID_PART_A, "A", null)

    every { relationRepository.countByPartId(UUID_PART_A) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.save(any()) } returns relationEntity
    every { relationRepository.findAllByPartId(UUID_PART_A) } returns mutableListOf(relationEntity)
    every { partRepository.findById(UUID_PART_A) } returns Optional.of(partEntity)
    every { partRepository.save(any()) } returns partEntity

    val partRelationWasAddedTo = partService.createPartPartTypeRelation(relation)

    verify(exactly = 1) { relationRepository.save(any()) }
    Assertions.assertEquals(UUID_PART_A, partRelationWasAddedTo.id)
  }

  private fun partWith(label: String?, model: String?) = DomainPart(
    id = null, label = label, manufacturer = null, model = model, serialNumber = null,
    vendor = null, purchasePrice = null, purchasePriceCurrency = null, firstUsedDate = null,
    boughtAt = null, retiredAt = null, partTypeRelations = emptyList(), createdAt = null)

  @Test
  fun `modifyPart rejects when body id does not match path id`() {
    val pathId = UUID_PART_A
    val mismatchedBodyId = UUID_PART_B
    val part = partWith(label = "Tire", model = null).copy(id = mismatchedBodyId)

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.modifyPart(pathId, part)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_ID_MISMATCH.code, ex.getError().code)
    verify(exactly = 0) { partRepository.save(any()) }
  }

  @Test
  fun `modifyPart returns not found when part does not exist even if body is unidentifiable`() {
    val pathId = UUID_PART_A
    val unidentifiablePart = partWith(label = null, model = null)

    every { partRepository.findById(pathId) } returns Optional.empty()

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.modifyPart(pathId, unidentifiablePart)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { partRepository.save(any()) }
  }

  @Test
  fun `modifyPart returns not found when part does not exist`() {
    val pathId = UUID_PART_A
    val part = partWith(label = "Tire", model = null)

    every { partRepository.findById(pathId) } returns Optional.empty()

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.modifyPart(pathId, part)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { partRepository.save(any()) }
  }

  @Test
  fun `modifyPart saves entity with path id when body id is null`() {
    val pathId = UUID_PART_A
    val part = partWith(label = "Tire", model = null)
    val savedEntitySlot = slot<PartEntity>()
    val foundEntity = PartEntity(pathId, "A", emptyList())
    val savedEntity = PartEntity(id = pathId, label = "Tire", partTypeRelations = emptyList())

    every { partRepository.findById(pathId) } returns Optional.of(foundEntity)
    every { partRepository.saveAndFlush(capture(savedEntitySlot)) } returns savedEntity

    val result = partService.modifyPart(pathId, part)

    Assertions.assertSame(foundEntity, savedEntitySlot.captured)
    Assertions.assertEquals(pathId, savedEntitySlot.captured.id)
    Assertions.assertEquals(pathId, result.id)
    verify(exactly = 1) { partRepository.saveAndFlush(any()) }
  }

  @Test
  fun `modifyPart accepts matching body id and path id`() {
    val pathId = UUID_PART_A
    val part = partWith(label = "Tire", model = null).copy(id = pathId)
    val savedEntity = PartEntity(id = pathId, label = "Tire", partTypeRelations = emptyList())

    every { partRepository.findById(pathId) } returns Optional.of(PartEntity(pathId, "A", emptyList()))
    every { partRepository.saveAndFlush(any()) } returns savedEntity

    val result = partService.modifyPart(pathId, part)

    Assertions.assertEquals(pathId, result.id)
    verify(exactly = 1) { partRepository.saveAndFlush(any()) }
  }

  @Test
  fun `modifyPart preserves existing relations when body clears them`() {
    val pathId = UUID_PART_A
    val relation = DomainPartPartTypeRelation(
      UUID_PART_A, UUID_PART_TYPE_CRANK, OffsetDateTime.now(), null, domainPartA, domainPartTypeCrank)
    val existingEntity = PartEntity(UUID_PART_A, "A", listOf(partStorageMapper.map(relation)))
    val body = partWith(label = "Tire", model = null)
    val savedSlot = slot<PartEntity>()

    every { partRepository.findById(pathId) } returns Optional.of(existingEntity)
    every { partRepository.saveAndFlush(capture(savedSlot)) } answers { savedSlot.captured }

    partService.modifyPart(pathId, body)

    Assertions.assertEquals(1, savedSlot.captured.partTypeRelations?.size)
    Assertions.assertEquals(UUID_PART_TYPE_CRANK, savedSlot.captured.partTypeRelations?.first()?.partTypeId)
  }

  @Test
  fun `modifyPart preserves existing relations when body omits partTypeRelations`() {
    val pathId = UUID_PART_A
    val relation = DomainPartPartTypeRelation(
      UUID_PART_A, UUID_PART_TYPE_CRANK, OffsetDateTime.now(), null, domainPartA, domainPartTypeCrank)
    val existingEntity = PartEntity(UUID_PART_A, "A", listOf(partStorageMapper.map(relation)))
    val body = partWith(label = "Tire", model = null).copy(partTypeRelations = null)
    val savedSlot = slot<PartEntity>()

    every { partRepository.findById(pathId) } returns Optional.of(existingEntity)
    every { partRepository.saveAndFlush(capture(savedSlot)) } answers { savedSlot.captured }

    partService.modifyPart(pathId, body)

    Assertions.assertEquals(1, savedSlot.captured.partTypeRelations?.size)
    Assertions.assertEquals(UUID_PART_TYPE_CRANK, savedSlot.captured.partTypeRelations?.first()?.partTypeId)
  }

  @Test
  fun `addPart rejects a part with neither label nor model`() {
    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.addPart(partWith(label = "  ", model = null))
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_NOT_IDENTIFIABLE.code, ex.getError().code)
    verify(exactly = 0) { partRepository.save(any()) }
  }

  @Test
  fun `addPart accepts a part identified by model only`() {
    val part = partWith(label = null, model = "GP5000")
    val savedEntity = partStorageMapper.map(part).apply { id = UUID_PART_A }
    every { partRepository.save(any()) } returns savedEntity

    val result = partService.addPart(part)

    Assertions.assertEquals(UUID_PART_A, result.id)
    verify(exactly = 1) { partRepository.save(any()) }
  }

  @Test
  fun `test offset date time to instance calculations conversions`() {
    var validFrom = OffsetDateTime.parse("2022-03-01T13:10:23+01")
    var validUntilExpected = OffsetDateTime.parse("2022-02-28T23:59:59+01")
    var validUntilExpectedUTC = OffsetDateTime.parse("2022-02-28T22:59:59Z")

    var validUntilCalculated = validFrom.truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS)

    Assertions.assertEquals(validUntilExpected, validUntilCalculated)
    Assertions.assertEquals(validUntilExpectedUTC, validUntilCalculated.toInstant().atOffset(ZoneOffset.UTC))

    validFrom = OffsetDateTime.parse("2022-03-01T00:00:00+01")
    validUntilExpected = OffsetDateTime.parse("2022-02-28T23:59:59+01")
    validUntilExpectedUTC = OffsetDateTime.parse("2022-02-28T22:59:59Z")

    validUntilCalculated = validFrom.truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS)

    Assertions.assertEquals(validUntilExpected, validUntilCalculated)
    Assertions.assertEquals(validUntilExpectedUTC, validUntilCalculated.toInstant().atOffset(ZoneOffset.UTC))

  }

  // when open-ended relation B to type Crank exists, relating A to type Crank shall terminate relation B to type Crank
  @Test
  fun `when open-ended relation B to Crank exists then A to Crank then B to Crank is terminated `() {
    val validFrom = OffsetDateTime.parse("2022-03-01T00:00:00+01")
    val expectedValidUntil = OffsetDateTime.parse("2022-02-28T22:59:59Z").toInstant()

    val relationA =
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK,
        validFrom.minus(10, ChronoUnit.DAYS), null, domainPartA, domainPartTypeCrank)
    val relationEntityA = partStorageMapper.map(relationA)
    val relationB =
      DomainPartPartTypeRelation(UUID_PART_B, UUID_PART_TYPE_CRANK, validFrom, null, domainPartA, domainPartTypeCrank)
    val partEntityB = PartEntity(UUID_PART_B, "B", null)

    val savedEntitySlot = slot<PartPartTypeRelationEntity>()
    val savedEntities : MutableList<PartPartTypeRelationEntity> = mutableListOf()

    every { relationRepository.countByPartId(UUID_PART_A) } returns 1
    every { relationRepository.countByPartId(UUID_PART_B) } returns 0
    every { relationRepository.countByPartIdAndValidUntilIsNull(UUID_PART_B) } returns 0
    every { relationRepository.countByPartIdAndPartTypeIdAndValidUntilIsNull(UUID_PART_B, UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 1
    every { relationRepository.findFirstByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns relationEntityA
    every { relationRepository.save(capture(savedEntitySlot)) } answers {
      val entity = savedEntitySlot.captured
      savedEntities.add(entity)
      entity
    }
    every { relationRepository.findAllByPartId(UUID_PART_B) } returns mutableListOf(partStorageMapper.map(relationB))
    every { partRepository.findById(UUID_PART_B) } returns Optional.of(partEntityB)
    every { partRepository.save(partEntityB) } returns partEntityB

    @SuppressWarnings("NP_NULL_ON_SOME_PATH") // lateinit -> false positive
    val partRelationWasAddedTo = partService.createPartPartTypeRelation(relationB)

    verify (exactly = 2) { relationRepository.save(ofType(PartPartTypeRelationEntity::class)) }
    verify { relationRepository.save(and( match { it.partId == UUID_PART_A  }, match { it.validUntil != null } )) }
    val persistedRelationEntityPartA = savedEntities.find { entity -> entity.partId == UUID_PART_A }
    val persistedRelationEntityPartB = savedEntities.find { entity -> entity.partId == UUID_PART_B }
    Assertions.assertEquals(expectedValidUntil, persistedRelationEntityPartA?.validUntil)
    Assertions.assertEquals(UUID_PART_B, partRelationWasAddedTo.id)
    Assertions.assertNotNull(persistedRelationEntityPartB)
    Assertions.assertNull(persistedRelationEntityPartB!!.validUntil)

  }

  @Test
  fun `modifyPart maps constraint violation to data invalid not server error`() {
    val pathId = UUID_PART_A
    val part = partWith(label = "Tire", model = null)
    val existingEntity = PartEntity(pathId, "A", emptyList())
    every { partRepository.findById(pathId) } returns Optional.of(existingEntity)
    every { partRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("CONSTRAINT_VIOLATION")

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.modifyPart(pathId, part)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_DATA_INVALID.code, ex.getError().code)
    Assertions.assertEquals(400, ex.getError().httpStatus)
  }

  @Test
  fun `modifyPart maps technical failure to server error`() {
    val pathId = UUID_PART_A
    val part = partWith(label = "Tire", model = null)
    val existingEntity = PartEntity(pathId, "A", emptyList())
    every { partRepository.findById(pathId) } returns Optional.of(existingEntity)
    every { partRepository.saveAndFlush(any()) } throws RuntimeException("db down")

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.modifyPart(pathId, part)
    }
    Assertions.assertEquals(ErrorCodesService.INTERNAL_SERVER_ERROR.code, ex.getError().code)
    Assertions.assertEquals(500, ex.getError().httpStatus)
  }

  @Test
  fun `createPartPartTypeRelation maps relation constraint violation to relation not valid`() {
    val validFrom = OffsetDateTime.now()
    val relation = DomainPartPartTypeRelation(UUID_PART_B, UUID_PART_TYPE_CRANK, validFrom, null, domainPartA, domainPartTypeCrank)
    val existingOpenRelation = partStorageMapper.map(
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, validFrom.minus(10, ChronoUnit.DAYS), null, domainPartA, domainPartTypeCrank))

    every { relationRepository.countByPartId(UUID_PART_B) } returns 0
    every { relationRepository.countByPartIdAndValidUntilIsNull(UUID_PART_B) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 1
    every { relationRepository.findFirstByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns existingOpenRelation
    every { relationRepository.save(any()) } throws DataIntegrityViolationException("CONSTRAINT_VIOLATION")

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.createPartPartTypeRelation(relation)
    }
    Assertions.assertEquals(ErrorCodesDomain.RELATION_NOT_VALID.code, ex.getError().code)
    Assertions.assertEquals(400, ex.getError().httpStatus)
  }

  @Test
  fun `createPartPartTypeRelation maps technical failure when modifying relation to server error`() {
    val validFrom = OffsetDateTime.now()
    val relation = DomainPartPartTypeRelation(UUID_PART_B, UUID_PART_TYPE_CRANK, validFrom, null, domainPartA, domainPartTypeCrank)
    val existingOpenRelation = partStorageMapper.map(
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, validFrom.minus(10, ChronoUnit.DAYS), null, domainPartA, domainPartTypeCrank))

    every { relationRepository.countByPartId(UUID_PART_B) } returns 0
    every { relationRepository.countByPartIdAndValidUntilIsNull(UUID_PART_B) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 1
    every { relationRepository.findFirstByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns existingOpenRelation
    every { relationRepository.save(any()) } throws RuntimeException("db down")

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partService.createPartPartTypeRelation(relation)
    }
    Assertions.assertEquals(ErrorCodesService.INTERNAL_SERVER_ERROR.code, ex.getError().code)
    Assertions.assertEquals(500, ex.getError().httpStatus)
  }

  @Test
  fun `createPartPartTypeRelation sets firstUsedDate when part has no prior date`() {
    val validFrom = OffsetDateTime.parse("2022-06-01T00:00:00Z")
    val relation = DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, validFrom, null, domainPartA, domainPartTypeCrank)
    val relationEntity = partStorageMapper.map(relation)
    val partEntity = PartEntity(UUID_PART_A, "A", null, firstUsedDate = null)

    every { relationRepository.countByPartId(UUID_PART_A) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.save(any()) } returns relationEntity
    every { relationRepository.findAllByPartId(UUID_PART_A) } returns mutableListOf(relationEntity)
    every { partRepository.findById(UUID_PART_A) } returns Optional.of(partEntity)
    every { partRepository.save(partEntity) } returns partEntity

    partService.createPartPartTypeRelation(relation)

    Assertions.assertEquals(validFrom.toInstant(), partEntity.firstUsedDate)
    verify(exactly = 1) { partRepository.save(partEntity) }
  }

  @Test
  fun `createPartPartTypeRelation moves firstUsedDate earlier when new relation is earlier`() {
    val existingValidFrom = OffsetDateTime.parse("2022-06-01T00:00:00Z")
    val earlierValidFrom = OffsetDateTime.parse("2021-01-01T00:00:00Z")
    val newRelation = DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, earlierValidFrom, null, domainPartA, domainPartTypeCrank)
    val newRelationEntity = partStorageMapper.map(newRelation)
    val existingRelationEntity = partStorageMapper.map(
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, existingValidFrom, null, domainPartA, domainPartTypeCrank))
    val partEntity = PartEntity(UUID_PART_A, "A", null, firstUsedDate = existingValidFrom.toInstant())

    every { relationRepository.countByPartId(UUID_PART_A) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.save(any()) } returns newRelationEntity
    every { relationRepository.findAllByPartId(UUID_PART_A) } returns mutableListOf(existingRelationEntity, newRelationEntity)
    every { partRepository.findById(UUID_PART_A) } returns Optional.of(partEntity)
    every { partRepository.save(partEntity) } returns partEntity

    partService.createPartPartTypeRelation(newRelation)

    Assertions.assertEquals(earlierValidFrom.toInstant(), partEntity.firstUsedDate)
    verify(exactly = 1) { partRepository.save(partEntity) }
  }

  @Test
  fun `createPartPartTypeRelation does not change firstUsedDate when new relation is not earlier`() {
    val existingValidFrom = OffsetDateTime.parse("2021-01-01T00:00:00Z")
    val laterValidFrom = OffsetDateTime.parse("2022-06-01T00:00:00Z")
    val newRelation = DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, laterValidFrom, null, domainPartA, domainPartTypeCrank)
    val newRelationEntity = partStorageMapper.map(newRelation)
    val existingRelationEntity = partStorageMapper.map(
      DomainPartPartTypeRelation(UUID_PART_A, UUID_PART_TYPE_CRANK, existingValidFrom, null, domainPartA, domainPartTypeCrank))
    val partEntity = PartEntity(UUID_PART_A, "A", null, firstUsedDate = existingValidFrom.toInstant())

    every { relationRepository.countByPartId(UUID_PART_A) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.save(any()) } returns newRelationEntity
    every { relationRepository.findAllByPartId(UUID_PART_A) } returns mutableListOf(existingRelationEntity, newRelationEntity)
    every { partRepository.findById(UUID_PART_A) } returns Optional.of(partEntity)

    partService.createPartPartTypeRelation(newRelation)

    Assertions.assertEquals(existingValidFrom.toInstant(), partEntity.firstUsedDate)
    verify(exactly = 0) { partRepository.save(any()) }
  }

  @Test
  fun `deletePart throws not found when part does not exist`() {
    val id = UUID_PART_A
    every { partRepository.existsById(id) } returns false
    every { partRepository.deleteById(id) } just Runs
    val ex = Assertions.assertThrows(ServiceException::class.java) { partService.deletePart(id) }
    Assertions.assertEquals(ErrorCodesDomain.PART_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { partRepository.deleteById(any()) }
  }

  @Test
  fun `deletePart maps constraint violation to FK error not not-found`() {
    val id = UUID_PART_A
    every { partRepository.existsById(id) } returns true
    every { partRepository.deleteById(id) } throws DataIntegrityViolationException("FK_PART_RELATION")
    val ex = Assertions.assertThrows(ServiceException::class.java) { partService.deletePart(id) }
    Assertions.assertEquals(ErrorCodesDomain.PART_HAS_FOREIGN_KEY_CONSTRAINT.code, ex.getError().code)
    Assertions.assertEquals(400, ex.getError().httpStatus)
  }

}

/**
 * Helper function to mock add createdAt if null
 */
private fun mockSavePart(partEntity: PartEntity) : PartEntity {
  with(partEntity) {
    val creationDate = createdAt ?: Instant.now()
    return PartEntity(id = id, label = label, partTypeRelations = partTypeRelations,
      boughtAt = boughtAt, createdAt = creationDate)
  }
}
