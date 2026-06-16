package de.cyclingsir.cetrack.part.domain

import de.cyclingsir.cetrack.bike.domain.BikeService
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartStorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeEntity
import de.cyclingsir.cetrack.part.storage.PartTypeDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartTypeRepository
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
import java.util.UUID

@ExtendWith(MockKExtension::class)
class PartTypeServiceTest {

  companion object {
    val UUID_PART_TYPE_A = UUID.randomUUID()
    val UUID_PART_TYPE_B = UUID.randomUUID()
    val UUID_BIKE = UUID.randomUUID()
  }

  @MockK
  private lateinit var repository: PartTypeRepository

  @MockK
  private lateinit var bikeMapper: BikeDomain2StorageMapper

  @MockK
  private lateinit var bikeService: BikeService

  private val partStorageMapper =
    PartStorageMapper(PartDomain2StorageMapperImpl(), PartTypeDomain2StorageMapperImpl(),
      PartPartTypeRelationDomain2StorageMapperImpl())

  private lateinit var partTypeService: PartTypeService

  @BeforeEach
  fun init() {
    MockKAnnotations.init(this)
    partTypeService = PartTypeService(repository, partStorageMapper, bikeMapper, bikeService)
  }

  private fun partTypeWith(name: String, id: UUID? = null) =
    DomainPartType(id = id, name = name, mandatory = false, partTypeRelations = emptyList(), bike = null, createdAt = null)

  @Test
  fun `modifyPartType rejects when body id does not match path id`() {
    val pathId = UUID_PART_TYPE_A
    val partType = partTypeWith(name = "Crank", id = UUID_PART_TYPE_B)

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partTypeService.modifyPartType(pathId, partType)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_TYPE_ID_MISMATCH.code, ex.getError().code)
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `modifyPartType returns not found when part type does not exist`() {
    val pathId = UUID_PART_TYPE_A
    val partType = partTypeWith(name = "Crank")

    every { repository.existsById(pathId) } returns false

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partTypeService.modifyPartType(pathId, partType)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_TYPE_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `modifyPartType returns not found even when body id is missing and name is blank`() {
    val pathId = UUID_PART_TYPE_A
    val unidentifiablePartType = partTypeWith(name = "")

    every { repository.existsById(pathId) } returns false

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      partTypeService.modifyPartType(pathId, unidentifiablePartType)
    }
    Assertions.assertEquals(ErrorCodesDomain.PART_TYPE_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `modifyPartType saves entity with path id when body id is null`() {
    val pathId = UUID_PART_TYPE_A
    val partType = partTypeWith(name = "Crank")
    val savedEntitySlot = slot<PartTypeEntity>()
    val savedEntity = PartTypeEntity(id = pathId, name = "Crank", mandatory = false, partTypeRelations = emptyList())

    every { repository.existsById(pathId) } returns true
    every { repository.save(capture(savedEntitySlot)) } returns savedEntity

    val result = partTypeService.modifyPartType(pathId, partType)

    Assertions.assertEquals(pathId, savedEntitySlot.captured.id)
    Assertions.assertEquals(pathId, result?.id)
    verify(exactly = 1) { repository.save(any()) }
  }

  @Test
  fun `deletePartType throws not found when part type does not exist`() {
    val id = UUID_PART_TYPE_A
    every { repository.existsById(id) } returns false
    every { repository.deleteById(id) } just Runs
    val ex = Assertions.assertThrows(ServiceException::class.java) { partTypeService.deletePartType(id) }
    Assertions.assertEquals(ErrorCodesDomain.PART_TYPE_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { repository.deleteById(any()) }
  }

  @Test
  fun `deletePartType maps constraint violation to FK error not not-found`() {
    val id = UUID_PART_TYPE_A
    every { repository.existsById(id) } returns true
    every { repository.deleteById(id) } throws DataIntegrityViolationException("FK_PART_TYPE_RELATION")
    val ex = Assertions.assertThrows(ServiceException::class.java) { partTypeService.deletePartType(id) }
    Assertions.assertEquals(ErrorCodesDomain.PART_TYPE_HAS_FOREIGN_KEY_CONSTRAINT.code, ex.getError().code)
    Assertions.assertEquals(400, ex.getError().httpStatus)
  }

  @Test
  fun `modifyPartType accepts matching body id and path id`() {
    val pathId = UUID_PART_TYPE_A
    val partType = partTypeWith(name = "Crank", id = pathId)
    val savedEntity = PartTypeEntity(id = pathId, name = "Crank", mandatory = false, partTypeRelations = emptyList())

    every { repository.existsById(pathId) } returns true
    every { repository.save(any()) } returns savedEntity

    val result = partTypeService.modifyPartType(pathId, partType)

    Assertions.assertEquals(pathId, result?.id)
    verify(exactly = 1) { repository.save(any()) }
  }
}
