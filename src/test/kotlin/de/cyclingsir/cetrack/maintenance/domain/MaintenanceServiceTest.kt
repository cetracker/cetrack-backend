package de.cyclingsir.cetrack.maintenance.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceDomain2StorageMapper
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceEventRepository
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceMileageDao
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceTaskEntity
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceTaskRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Pure validation branches - the persistence-facing behavior lives in
 * MaintenanceCrudIT (PG Testcontainers).
 */
class MaintenanceServiceTest {

    private val taskRepository = mockk<MaintenanceTaskRepository>()
    private val eventRepository = mockk<MaintenanceEventRepository>()
    private val mileageDao = mockk<MaintenanceMileageDao>()
    private val service = MaintenanceService(taskRepository, eventRepository, mileageDao, MaintenanceDomain2StorageMapper())

    private fun aTask(bikeId: UUID = UUID.randomUUID(), distanceInterval: Long? = 800_000L, timeInterval: Long? = null) =
        DomainMaintenanceTask(bikeId = bikeId, name = "chain wax", distanceInterval = distanceInterval, timeInterval = timeInterval)

    @Test
    fun `neither interval set is rejected`() {
        val ex = assertThrows<ServiceException> {
            service.addMaintenanceTask(aTask(distanceInterval = null, timeInterval = null))
        }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID, ex.getError())
    }

    @Test
    fun `constraint violation on save maps to MAINTENANCE_TASK_DATA_INVALID`() {
        every { taskRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("fk violation")

        val ex = assertThrows<ServiceException> { service.addMaintenanceTask(aTask()) }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID, ex.getError())
    }

    @Test
    fun `bikeId change on modify is rejected`() {
        val taskId = UUID.randomUUID()
        val originalBikeId = UUID.randomUUID()
        val existing = MaintenanceTaskEntity(id = taskId, bikeId = originalBikeId, name = "chain wax", distanceInterval = 800_000L)
        every { taskRepository.findById(taskId) } returns Optional.of(existing)

        val ex = assertThrows<ServiceException> {
            service.modifyMaintenanceTask(taskId, aTask(bikeId = UUID.randomUUID()))
        }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID, ex.getError())
    }

    @Test
    fun `modify with unchanged bikeId and neither interval set is rejected`() {
        val taskId = UUID.randomUUID()
        val bikeId = UUID.randomUUID()
        val existing = MaintenanceTaskEntity(id = taskId, bikeId = bikeId, name = "chain wax", distanceInterval = 800_000L)
        every { taskRepository.findById(taskId) } returns Optional.of(existing)

        val ex = assertThrows<ServiceException> {
            service.modifyMaintenanceTask(taskId, aTask(bikeId = bikeId, distanceInterval = null, timeInterval = null))
        }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID, ex.getError())
    }

    @Test
    fun `get on unknown task is not found`() {
        val taskId = UUID.randomUUID()
        every { taskRepository.findById(taskId) } returns Optional.empty()

        val ex = assertThrows<ServiceException> { service.getMaintenanceTask(taskId) }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_NOT_FOUND, ex.getError())
    }

    @Test
    fun `delete task deletes events before the task itself`() {
        val taskId = UUID.randomUUID()
        val existing = MaintenanceTaskEntity(id = taskId, bikeId = UUID.randomUUID(), name = "chain wax", distanceInterval = 800_000L)
        every { taskRepository.findById(taskId) } returns Optional.of(existing)
        every { eventRepository.deleteByMaintenanceTaskId(taskId) } returns Unit
        every { taskRepository.deleteById(taskId) } returns Unit

        service.deleteMaintenanceTask(taskId)

        verifyOrder {
            eventRepository.deleteByMaintenanceTaskId(taskId)
            taskRepository.deleteById(taskId)
        }
    }

    @Test
    fun `delete on unknown task is not found and never touches events`() {
        val taskId = UUID.randomUUID()
        every { taskRepository.findById(taskId) } returns Optional.empty()

        val ex = assertThrows<ServiceException> { service.deleteMaintenanceTask(taskId) }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_NOT_FOUND, ex.getError())
    }

    @Test
    fun `event on unknown task is not found`() {
        val taskId = UUID.randomUUID()
        every { taskRepository.findById(taskId) } returns Optional.empty()

        val ex = assertThrows<ServiceException> { service.addMaintenanceEvent(taskId, Instant.now()) }
        assertEquals(ErrorCodesDomain.MAINTENANCE_TASK_NOT_FOUND, ex.getError())
    }

    @Test
    fun `delete unknown event is not found`() {
        val taskId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        every { eventRepository.findByIdAndMaintenanceTaskId(eventId, taskId) } returns null

        val ex = assertThrows<ServiceException> { service.deleteMaintenanceEvent(taskId, eventId) }
        assertEquals(ErrorCodesDomain.MAINTENANCE_EVENT_NOT_FOUND, ex.getError())
    }
}
