package de.cyclingsir.cetrack.bike.domain

import de.cyclingsir.cetrack.bike.storage.BikeEntity
import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesService
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.part.domain.PartTypeServiceTest.Companion.UUID_PART_TYPE_A
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import net.bytebuddy.matcher.ElementMatchers.any
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class BikeServiceTest {

  companion object {
    val UUID_BIKE_A = UUID.randomUUID()
    val UUID_BIKE_B = UUID.randomUUID()
  }

  @MockK
  private lateinit var repository: BikeRepository

  @MockK
  private lateinit var mapper: BikeDomain2StorageMapper

  private lateinit var bikeService: BikeService

  @BeforeEach
  fun init() {
    MockKAnnotations.init(this)
    bikeService = BikeService(repository, mapper)
  }

  private fun bikeWith(model: String, id: UUID? = null) =
    DomainBike(model = model, manufacturer = null, id = id, boughtAt = null, retiredAt = null, createdAt = null)

  @Test
  fun `modifyBike rejects when body id does not match path id`() {
    val pathId = UUID_BIKE_A
    val bike = bikeWith(model = "Tarmac", id = UUID_BIKE_B)

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      bikeService.modifyBike(pathId, bike)
    }
    Assertions.assertEquals(ErrorCodesDomain.BIKE_ID_MISMATCH.code, ex.getError().code)
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `modifyBike returns not found when bike does not exist`() {
    val pathId = UUID_BIKE_A
    val bike = bikeWith(model = "Tarmac")

    every { repository.existsById(pathId) } returns false

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      bikeService.modifyBike(pathId, bike)
    }
    Assertions.assertEquals(ErrorCodesDomain.BIKE_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `modifyBike saves entity with path id when body id is null`() {
    val pathId = UUID_BIKE_A
    val bike = bikeWith(model = "Tarmac")
    val mappedEntity = BikeEntity(id = null, model = "Tarmac")
    val savedEntity = BikeEntity(id = pathId, model = "Tarmac")
    val savedEntitySlot = slot<BikeEntity>()

    every { repository.existsById(pathId) } returns true
    every { mapper.map(bike) } returns mappedEntity
    every { repository.save(capture(savedEntitySlot)) } returns savedEntity
    every { mapper.map(savedEntity) } returns bikeWith(model = "Tarmac", id = pathId)

    val result = bikeService.modifyBike(pathId, bike)

    Assertions.assertEquals(pathId, savedEntitySlot.captured.id)
    Assertions.assertEquals(pathId, result?.id)
    verify(exactly = 1) { repository.save(any()) }
  }

  @Test
  fun `deleteBike throws not found when bike does not exist`() {
    val id = UUID_BIKE_A
    every { repository.existsById(id) } returns false
    every { repository.deleteById(id) } just Runs
    val ex = Assertions.assertThrows(ServiceException::class.java) { bikeService.deleteBike(id) }
    Assertions.assertEquals(ErrorCodesDomain.BIKE_NOT_FOUND.code, ex.getError().code)
    verify(exactly = 0) { repository.deleteById(any()) }
  }

  @Test
  fun `modifyBike accepts matching body id and path id`() {
    val pathId = UUID_BIKE_A
    val bike = bikeWith(model = "Tarmac", id = pathId)
    val mappedEntity = BikeEntity(id = pathId, model = "Tarmac")
    val savedEntity = BikeEntity(id = pathId, model = "Tarmac")

    every { repository.existsById(pathId) } returns true
    every { mapper.map(bike) } returns mappedEntity
    every { repository.save(any()) } returns savedEntity
    every { mapper.map(savedEntity) } returns bikeWith(model = "Tarmac", id = pathId)

    val result = bikeService.modifyBike(pathId, bike)

    Assertions.assertEquals(pathId, result?.id)
    verify(exactly = 1) { repository.save(any()) }
  }

  @Test
  fun `modifyBike maps technical failure to server error not not-found`() {
    val pathId = UUID_BIKE_A
    val bike = bikeWith(model = "Daytona", id = pathId)
    val savedEntity = BikeEntity(id = pathId, model = "Daytona")
    every { repository.existsById(pathId) } returns true
    every { mapper.map(bike) } returns savedEntity
    every { repository.save(any()) } throws RuntimeException("db down")

    val ex = Assertions.assertThrows(ServiceException::class.java) {
      bikeService.modifyBike(pathId, bike)
    }
    Assertions.assertEquals(ErrorCodesService.INTERNAL_SERVER_ERROR.code, ex.getError().code)
    Assertions.assertEquals(500, ex.getError().httpStatus)
  }

}
