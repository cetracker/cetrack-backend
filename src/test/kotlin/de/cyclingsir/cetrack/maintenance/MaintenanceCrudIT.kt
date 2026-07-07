package de.cyclingsir.cetrack.maintenance

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.maintenance.domain.DomainMaintenanceTask
import de.cyclingsir.cetrack.maintenance.domain.MaintenanceService
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceMileageDao
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
    @Autowired private lateinit var mileageDao: MaintenanceMileageDao

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

    @Test
    fun `distanceBetween sums tours within bounds, boundary is inclusive on toInclusive only`() {
        val bikeId = newBike()
        val t1 = Instant.parse("2025-01-01T00:00:00Z")
        val t2 = Instant.parse("2025-02-01T00:00:00Z")
        val t3 = Instant.parse("2025-03-01T00:00:00Z")
        seedTour(bikeId, t1, 100_000) // on fromExclusive boundary - excluded
        seedTour(bikeId, t2, 200_000) // strictly inside
        seedTour(bikeId, t3, 300_000) // on toInclusive boundary - included

        assertThat(mileageDao.distanceBetween(bikeId, t1, t3)).isEqualTo(500_000L)
        assertThat(mileageDao.distanceBetween(bikeId, null, t2)).isEqualTo(300_000L)
        assertThat(mileageDao.distanceBetween(bikeId, t2, null)).isEqualTo(300_000L)
        assertThat(mileageDao.distanceBetween(bikeId, null, null)).isEqualTo(600_000L)
        assertThat(mileageDao.distanceBetween(bikeId, t3, null)).isEqualTo(0L)
    }

    @Test
    fun `getMaintenanceEvents partitions bike mileage across event intervals`() {
        val bikeId = newBike()
        val task = service.addMaintenanceTask(
            DomainMaintenanceTask(bikeId = bikeId, name = "chain wax", distanceInterval = 800_000L)
        )
        val beforeFirst = Instant.parse("2025-01-01T00:00:00Z")
        val betweenEvents = Instant.parse("2025-02-01T00:00:00Z")
        val afterSecond = Instant.parse("2025-04-01T00:00:00Z")
        seedTour(bikeId, beforeFirst, 100_000)
        seedTour(bikeId, betweenEvents, 200_000)
        seedTour(bikeId, afterSecond, 300_000)

        val firstEventAt = Instant.parse("2025-01-15T00:00:00Z")
        val secondEventAt = Instant.parse("2025-03-01T00:00:00Z")
        service.addMaintenanceEvent(task.id!!, firstEventAt)
        service.addMaintenanceEvent(task.id!!, secondEventAt)

        val events = service.getMaintenanceEvents(task.id!!)
        assertThat(events).hasSize(2)
        val (newest, oldest) = events[0] to events[1]
        assertThat(oldest.performedAt).isEqualTo(firstEventAt)
        assertThat(oldest.distanceSincePrevious).isEqualTo(100_000L) // since first tour
        assertThat(newest.performedAt).isEqualTo(secondEventAt)
        assertThat(newest.distanceSincePrevious).isEqualTo(200_000L) // between the two events

        val distanceSinceLast = service.getMaintenanceTask(task.id!!).due?.distanceSinceLast
        val partitionedTotal = events.sumOf { it.distanceSincePrevious ?: 0 } + (distanceSinceLast ?: 0)
        assertThat(partitionedTotal).isEqualTo(600_000L) // sum of all three tours
    }
}
