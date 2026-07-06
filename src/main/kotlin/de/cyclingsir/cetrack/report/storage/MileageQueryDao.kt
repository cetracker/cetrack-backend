package de.cyclingsir.cetrack.report.storage

import de.cyclingsir.cetrack.report.domain.DomainMileageItem
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Mileage derivation (domain-model.md §5): a tour counts for a component iff
 * an active Mounting on the tour's bike COVERS the tour's whole interval
 * [started_at, started_at + elapsed] - a mid-tour swap counts for neither
 * component (decision recorded in issues/plans/CE-0083.md §3).
 * Pure reporting: JdbcTemplate, no entities.
 */
@Repository
class MileageQueryDao(private val jdbc: JdbcTemplate) {

    companion object {
        // tour interval is half-open [start, start+elapsed) so a swap exactly at
        // ride end still credits the outgoing component; a zero-length/duration-
        // less tour degrades to point containment (an empty range would be
        // "covered" by every mounting)
        private const val TOUR_COVERED_BY_MOUNTING = """
            t.bike_id = mp.bike_id
            AND t.started_at IS NOT NULL
            AND CASE
                WHEN COALESCE(t.duration_elapsed, t.duration_recorded, t.duration_moving, 0) = 0
                THEN tstzrange(m.mounted_at, m.dismounted_at, '[)') @> t.started_at
                ELSE tstzrange(m.mounted_at, m.dismounted_at, '[)') @>
                     tstzrange(t.started_at,
                               t.started_at + make_interval(secs =>
                                   COALESCE(t.duration_elapsed, t.duration_recorded, t.duration_moving, 0)),
                               '[)')
                END"""

        private const val SUMS = """
            COALESCE(SUM(t.distance), 0)        AS distance,
            COALESCE(SUM(t.duration_moving), 0) AS duration_moving,
            COALESCE(SUM(t.ascent), 0)          AS ascent,
            COALESCE(SUM(t.descent), 0)         AS descent,
            COALESCE(SUM(t.power_total), 0)     AS power_total"""
    }

    fun perComponent(componentId: UUID?, bikeId: UUID?, from: Instant?, to: Instant?): List<DomainMileageItem> {
        val sql = StringBuilder(
            """SELECT c.id AS component_id, c.label, c.manufacturer, c.model, c.serial_number, $SUMS
               FROM component c
               JOIN mounting m ON m.component_id = c.id
               JOIN mount_point mp ON mp.id = m.mount_point_id
               JOIN tour t ON $TOUR_COVERED_BY_MOUNTING
               WHERE 1 = 1"""
        )
        val params = mutableListOf<Any>()
        componentId?.let { sql.append(" AND c.id = ?"); params.add(it) }
        bikeId?.let { sql.append(" AND mp.bike_id = ?"); params.add(it) }
        from?.let { sql.append(" AND t.started_at >= ?"); params.add(java.sql.Timestamp.from(it)) }
        to?.let { sql.append(" AND t.started_at <= ?"); params.add(java.sql.Timestamp.from(it)) }
        sql.append(" GROUP BY c.id, c.label, c.manufacturer, c.model, c.serial_number ORDER BY c.label")
        return jdbc.query(sql.toString(), { rs, _ -> componentRow(rs) }, *params.toTypedArray())
    }

    /**
     * scope=bikes: total per bike over its tours; with componentId it becomes
     * the per-bike breakdown of that component's mileage (mounting-covered
     * tours only).
     */
    fun perBike(componentId: UUID?, bikeId: UUID?, from: Instant?, to: Instant?): List<DomainMileageItem> {
        val sql = StringBuilder(
            if (componentId == null) {
                """SELECT b.id AS bike_id, b.name AS bike_name, b.model AS bike_model, $SUMS
                   FROM bike b
                   JOIN tour t ON t.bike_id = b.id
                   WHERE 1 = 1"""
            } else {
                """SELECT b.id AS bike_id, b.name AS bike_name, b.model AS bike_model, $SUMS
                   FROM bike b
                   JOIN mount_point mp ON mp.bike_id = b.id
                   JOIN mounting m ON m.mount_point_id = mp.id AND m.component_id = ?
                   JOIN tour t ON $TOUR_COVERED_BY_MOUNTING
                   WHERE 1 = 1"""
            }
        )
        val params = mutableListOf<Any>()
        componentId?.let { params.add(it) }
        bikeId?.let { sql.append(" AND b.id = ?"); params.add(it) }
        from?.let { sql.append(" AND t.started_at >= ?"); params.add(java.sql.Timestamp.from(it)) }
        to?.let { sql.append(" AND t.started_at <= ?"); params.add(java.sql.Timestamp.from(it)) }
        sql.append(" GROUP BY b.id, b.name, b.model ORDER BY b.name NULLS LAST, b.model")
        return jdbc.query(sql.toString(), { rs, _ -> bikeRow(rs) }, *params.toTypedArray())
    }

    private fun componentRow(rs: ResultSet) = DomainMileageItem(
        componentId = rs.getObject("component_id", UUID::class.java),
        label = rs.getString("label"),
        manufacturer = rs.getString("manufacturer"),
        model = rs.getString("model"),
        serialNumber = rs.getString("serial_number"),
        distance = rs.getLong("distance"),
        durationMoving = rs.getLong("duration_moving"),
        ascent = rs.getLong("ascent"),
        descent = rs.getLong("descent"),
        powerTotal = rs.getLong("power_total")
    )

    private fun bikeRow(rs: ResultSet) = DomainMileageItem(
        bikeId = rs.getObject("bike_id", UUID::class.java),
        bikeName = rs.getString("bike_name"),
        bikeModel = rs.getString("bike_model"),
        distance = rs.getLong("distance"),
        durationMoving = rs.getLong("duration_moving"),
        ascent = rs.getLong("ascent"),
        descent = rs.getLong("descent"),
        powerTotal = rs.getLong("power_total")
    )
}
