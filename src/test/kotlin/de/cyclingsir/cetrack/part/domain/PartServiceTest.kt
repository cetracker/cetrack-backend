package de.cyclingsir.cetrack.part.domain;

import de.cyclingsir.cetrack.bike.domain.DomainBike
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
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
@ExtendWith(MockKExtension::class)
class PartServiceTest {

  companion object {
    val UUID_PART_A = UUID.randomUUID()
    val UUID_PART_B = UUID.randomUUID()
    val UUID_PART_C = UUID.randomUUID()
    val UUID_PART_TYPE_CRANK = UUID.randomUUID()
    val UUID_PART_TYPE_FRONT_TIRE = UUID.randomUUID()
    val UUID_PART_TYPE_REAR_TIRE = UUID.randomUUID()
    val UUID_BIKE = UUID.randomUUID()
  }

  @MockK
  private lateinit var partRepository: PartRepository

  @MockK
  private lateinit var relationRepository: PartPartTypeRelationRepository

  private var bike =  DomainBike("Bike", "Manufacturer", UUID_BIKE, null, null, null)
  private var domainPartA = DomainPart(UUID_PART_A, "A", null, null, emptyList(), null)
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

    every { relationRepository.countByPartId(UUID_PART_A) } returns 0
    every { relationRepository.countByPartTypeIdAndValidUntilIsNull(UUID_PART_TYPE_CRANK) } returns 0
    every { relationRepository.save(any()) } returns relationEntity
    every { partRepository.findById(UUID_PART_A) } returns Optional.of(PartEntity(UUID_PART_A, "A", null))

    val partRelationWasAddedTo = partService.createPartPartTypeRelation(relation)

    verify(exactly = 1) { relationRepository.save(any()) }
    Assertions.assertEquals(UUID_PART_A, partRelationWasAddedTo.id)
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
    val savedEntities : MutableList<PartPartTypeRelationEntity> = mutableListOf();

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
    every { partRepository.findById(UUID_PART_B) } returns Optional.of(partEntityB)

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

}

/**
 * Helper function to mock add createdAt if null
 */
private fun mockSavePart(partEntity: PartEntity) : PartEntity {
  with(partEntity) {
    val creationDate = createdAt ?: Instant.now()
    return PartEntity(id, name, partTypeRelations, boughtAt, creationDate)
  }
}
