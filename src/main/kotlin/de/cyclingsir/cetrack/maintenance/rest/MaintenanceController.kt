package de.cyclingsir.cetrack.maintenance.rest

import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceEvent
import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceEventInput
import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceTask
import de.cyclingsir.cetrack.infrastructure.api.model.MaintenanceTaskInput
import de.cyclingsir.cetrack.infrastructure.api.rest.MaintenanceTasksApi
import de.cyclingsir.cetrack.maintenance.domain.MaintenanceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class MaintenanceController(
    private val service: MaintenanceService,
    private val mapper: MaintenanceDomain2ApiMapper,
) : MaintenanceTasksApi {

    override fun getMaintenanceTasks(
        @Valid @RequestParam(value = "bikeId", required = false) bikeId: UUID?,
        @Valid @RequestParam(value = "due", required = false) due: Boolean?
    ): ResponseEntity<List<MaintenanceTask>> =
        ResponseEntity.ok(service.getMaintenanceTasks(bikeId, due).map(mapper::map))

    override fun getMaintenanceTask(@PathVariable("taskId") taskId: UUID): ResponseEntity<MaintenanceTask> =
        ResponseEntity.ok(mapper.map(service.getMaintenanceTask(taskId)))

    override fun createMaintenanceTask(
        @Valid @RequestBody maintenanceTaskInput: MaintenanceTaskInput
    ): ResponseEntity<MaintenanceTask> {
        val added = service.addMaintenanceTask(mapper.map(maintenanceTaskInput))
        return ResponseEntity
            .created(URI.create("/api/maintenanceTasks/${added.id}"))
            .body(mapper.map(added))
    }

    override fun modifyMaintenanceTask(
        @PathVariable("taskId") taskId: UUID,
        @Valid @RequestBody maintenanceTaskInput: MaintenanceTaskInput
    ): ResponseEntity<MaintenanceTask> =
        ResponseEntity.ok(mapper.map(service.modifyMaintenanceTask(taskId, mapper.map(maintenanceTaskInput))))

    override fun deleteMaintenanceTask(@PathVariable("taskId") taskId: UUID): ResponseEntity<Unit> {
        service.deleteMaintenanceTask(taskId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    override fun getMaintenanceEvents(@PathVariable("taskId") taskId: UUID): ResponseEntity<List<MaintenanceEvent>> =
        ResponseEntity.ok(service.getMaintenanceEvents(taskId).map(mapper::map))

    override fun createMaintenanceEvent(
        @PathVariable("taskId") taskId: UUID,
        @Valid @RequestBody maintenanceEventInput: MaintenanceEventInput
    ): ResponseEntity<MaintenanceEvent> {
        val added = service.addMaintenanceEvent(taskId, maintenanceEventInput.performedAt.toInstant())
        return ResponseEntity
            .created(URI.create("/api/maintenanceTasks/$taskId/events/${added.id}"))
            .body(mapper.map(added))
    }

    override fun deleteMaintenanceEvent(
        @PathVariable("taskId") taskId: UUID,
        @PathVariable("eventId") eventId: UUID
    ): ResponseEntity<Unit> {
        service.deleteMaintenanceEvent(taskId, eventId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
