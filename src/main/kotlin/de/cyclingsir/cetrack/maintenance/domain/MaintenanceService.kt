package de.cyclingsir.cetrack.maintenance.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceDomain2StorageMapper
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceEventRepository
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceMileageDao
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceTaskEntity
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceTaskRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class MaintenanceService(
    private val taskRepository: MaintenanceTaskRepository,
    private val eventRepository: MaintenanceEventRepository,
    private val mileageDao: MaintenanceMileageDao,
    private val mapper: MaintenanceDomain2StorageMapper,
) {

    @Transactional(readOnly = true)
    fun getMaintenanceTasks(bikeId: UUID?, due: Boolean?): List<DomainMaintenanceTask> {
        val entities = bikeId?.let { taskRepository.findAllByBikeId(it) } ?: taskRepository.findAll()
        return entities.map(::withDue).filter { due == null || it.due?.due == due }
    }

    @Transactional(readOnly = true)
    fun getMaintenanceTask(taskId: UUID): DomainMaintenanceTask = withDue(findTaskOrThrow(taskId))

    @Transactional
    fun addMaintenanceTask(task: DomainMaintenanceTask): DomainMaintenanceTask {
        requireAtLeastOneInterval(task)
        val entity = try {
            taskRepository.saveAndFlush(mapper.map(task))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID,
                "Maintenance task references an unknown bike or violates a constraint.", e)
        }
        return withDue(entity)
    }

    @Transactional
    fun modifyMaintenanceTask(taskId: UUID, task: DomainMaintenanceTask): DomainMaintenanceTask {
        val existing = findTaskOrThrow(taskId)
        requireAtLeastOneInterval(task)
        if (existing.bikeId != task.bikeId) {
            throw ServiceException(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID,
                "bikeId is immutable; delete and recreate the task instead.")
        }
        existing.name = task.name
        existing.distanceInterval = task.distanceInterval
        existing.timeInterval = task.timeInterval
        val entity = try {
            taskRepository.saveAndFlush(existing)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID,
                "Maintenance task references an unknown bike or violates a constraint.", e)
        }
        return withDue(entity)
    }

    /** The task aggregate owns its events (domain-model.md §3) - delete them first. */
    @Transactional
    fun deleteMaintenanceTask(taskId: UUID) {
        findTaskOrThrow(taskId)
        eventRepository.deleteByMaintenanceTaskId(taskId)
        taskRepository.deleteById(taskId)
    }

    @Transactional(readOnly = true)
    fun getMaintenanceEvents(taskId: UUID): List<DomainMaintenanceEvent> {
        findTaskOrThrow(taskId)
        return eventRepository.findAllByMaintenanceTaskIdOrderByPerformedAtDesc(taskId).map(mapper::map)
    }

    @Transactional
    fun addMaintenanceEvent(taskId: UUID, performedAt: Instant): DomainMaintenanceEvent {
        findTaskOrThrow(taskId)
        val entity = eventRepository.saveAndFlush(
            mapper.map(DomainMaintenanceEvent(maintenanceTaskId = taskId, performedAt = performedAt))
        )
        return mapper.map(entity)
    }

    @Transactional
    fun deleteMaintenanceEvent(taskId: UUID, eventId: UUID) {
        eventRepository.findByIdAndMaintenanceTaskId(eventId, taskId)
            ?: throw ServiceException(ErrorCodesDomain.MAINTENANCE_EVENT_NOT_FOUND)
        eventRepository.deleteById(eventId)
    }

    private fun findTaskOrThrow(taskId: UUID): MaintenanceTaskEntity =
        taskRepository.findById(taskId).orElseThrow { ServiceException(ErrorCodesDomain.MAINTENANCE_TASK_NOT_FOUND) }

    private fun requireAtLeastOneInterval(task: DomainMaintenanceTask) {
        if (task.distanceInterval == null && task.timeInterval == null) {
            throw ServiceException(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID,
                "At least one of distanceInterval / timeInterval is required.")
        }
    }

    private fun withDue(entity: MaintenanceTaskEntity): DomainMaintenanceTask {
        val lastEvent = eventRepository.findFirstByMaintenanceTaskIdOrderByPerformedAtDesc(entity.id!!)
        val sinceLast = mileageDao.sinceLast(entity.bikeId, lastEvent?.performedAt)
        val due = DueCalculator.calculate(
            distanceInterval = entity.distanceInterval,
            timeInterval = entity.timeInterval,
            lastPerformedAt = lastEvent?.performedAt,
            distanceSinceLast = sinceLast.distance,
            firstTourAt = sinceLast.firstTourAt,
            now = Instant.now()
        )
        return mapper.map(entity).copy(due = due)
    }
}
