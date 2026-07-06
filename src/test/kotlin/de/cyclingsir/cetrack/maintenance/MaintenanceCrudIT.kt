package de.cyclingsir.cetrack.maintenance

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceTask
import de.cyclingsir.cetrack.maintenance.domain.MaintenanceService
import de.cyclingsir.cetrack.support.PostgreSQLContainerIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class MaintenanceCrudIT : PostgreSQLContainerIT() {

    @Autowired private lateinit var service: MaintenanceService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun newBike(): UUID {
        val bikeId = UUID.randomUUID()
        jdbc.update("INSERT INTO bike (id, model) VALUES (?, ?)", bikeId, "seed bike")
        return bikeId
    }

    /**
     * The Postgres container is a JVM-wide singleton (PostgreSQLContainerIT) shared by every
     * IT class, so a row left with SQL NULLs in a column TourEntity maps as non-null Kotlin
     * (title, ascent, descent, ...) would break TourRepository.findAll() in unrelated tests.
     */
    private fun seedTour(bikeId: UUID, startedAt: Instant, distance: Int) {
        jdbc.update(
            """INSERT INTO tour (id, bike_id, started_at, start_year, start_month, start_day,
                                  title, distance, duration_moving, ascent, descent, power_total)
               VALUES (?, ?, ?, 2025, 1, 1, ?, ?, 0, 0, 0, 0)""",
            UUID.randomUUID(), bikeId, java.sql.Timestamp.from(startedAt), "seed tour", distance
        )
    }

    @Test
    fun `create modify delete round trip, both intervals as Long`() {
        val bikeId = newBike()
        val task = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = bikeId, name = "chain wax", distanceInterval = 800_000L, timeInterval = 90 * 86_400L)
        )
        assertThat(task.id).isNotNull()
        assertThat(task.distanceInterval).isEqualTo(800_000L)
        assertThat(task.timeInterval).isEqualTo(90 * 86_400L)

        val modified = service.modifyMaintenanceTask(task.id!!, task.copy(name = "renewed wax"))
        assertThat(modified.name).isEqualTo("renewed wax")

        service.deleteMaintenanceTask(task.id!!)
        assertThrows<ServiceException> { service.getMaintenanceTask(task.id!!) }
    }

    @Test
    fun `bikeId is immutable on modify`() {
        val bikeId = newBike()
        val task = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = bikeId, name = "chain wax", distanceInterval = 800_000L)
        )
        val ex = assertThrows<ServiceException> {
            service.modifyMaintenanceTask(task.id!!, task.copy(bikeId = newBike()))
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID)
    }

    @Test
    fun `unknown bikeId on create is rejected as invalid data`() {
        val ex = assertThrows<ServiceException> {
            service.addMaintenanceTask(
                DomainMaintenanceTask(bikeId = UUID.randomUUID(), name = "orphan", distanceInterval = 1L)
            )
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.MAINTENANCE_TASK_DATA_INVALID)
    }

    @Test
    fun `event create and newest-first listing, delete task cascades events`() {
        val bikeId = newBike()
        val task = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = bikeId, name = "chain wax", distanceInterval = 800_000L)
        )
        val older = Instant.parse("2025-01-01T00:00:00Z")
        val newer = Instant.parse("2025-06-01T00:00:00Z")
        service.addMaintenanceEvent(task.id!!, older)
        val newestEvent = service.addMaintenanceEvent(task.id!!, newer)

        val events = service.getMaintenanceEvents(task.id!!)
        assertThat(events).hasSize(2)
        assertThat(events.first().id).isEqualTo(newestEvent.id)
        assertThat(events.map { it.performedAt }).containsExactly(newer, older)

        service.deleteMaintenanceTask(task.id!!)
        assertThrows<ServiceException> { service.getMaintenanceEvents(task.id!!) }
    }

    @Test
    fun `delete event removes a single mistakenly recorded occurrence`() {
        val bikeId = newBike()
        val task = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = bikeId, name = "chain wax", distanceInterval = 800_000L)
        )
        val event = service.addMaintenanceEvent(task.id!!, Instant.now())

        service.deleteMaintenanceEvent(task.id!!, event.id!!)

        assertThat(service.getMaintenanceEvents(task.id!!)).isEmpty()
    }

    @Test
    fun `due=true filter - one bike due by distance, one not`() {
        val dueBikeId = newBike()
        seedTour(dueBikeId, Instant.parse("2025-01-01T00:00:00Z"), 900_000)
        val notDueBikeId = newBike()
        seedTour(notDueBikeId, Instant.parse("2025-01-01T00:00:00Z"), 100_000)

        val dueTask = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = dueBikeId, name = "due wax", distanceInterval = 800_000L)
        )
        val notDueTask = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = notDueBikeId, name = "fresh wax", distanceInterval = 800_000L)
        )

        assertThat(service.getMaintenanceTask(dueTask.id!!).due?.due).isTrue()
        assertThat(service.getMaintenanceTask(notDueTask.id!!).due?.due).isFalse()

        val dueOnly = service.getMaintenanceTasks(bikeId = null, due = true)
        assertThat(dueOnly.map { it.id }).contains(dueTask.id).doesNotContain(notDueTask.id)
    }
}
