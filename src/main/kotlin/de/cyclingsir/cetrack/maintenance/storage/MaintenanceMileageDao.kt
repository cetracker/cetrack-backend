package de.cyclingsir.cetrack.maintenance.storage

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Distance-since and first-tour-baseline for one bike (domain-model.md §5.4
 * "Maintenance due"). Pure reporting: JdbcTemplate, no entities.
 */
@Repository
class MaintenanceMileageDao(private val jdbc: JdbcTemplate) {

    data class SinceLast(val distance: Long, val firstTourAt: Instant?)

    /**
     * distance = SUM(tour.distance) for the bike, restricted to tours after
     * lastPerformedAt when given (all tours otherwise); firstTourAt is the
     * bike's earliest tour, only meaningful when lastPerformedAt is null (no
     * event yet - full-history baseline, decision CE-0088 §4). The filter is
     * appended only when lastPerformedAt is non-null to avoid PG's "could not
     * determine data type" on a bound JDBC null.
     */
    fun sinceLast(bikeId: UUID, lastPerformedAt: Instant?): SinceLast {
        val sql = StringBuilder(
            "SELECT COALESCE(SUM(distance), 0) AS dist, MIN(started_at) AS first_tour FROM tour WHERE bike_id = ?"
        )
        val params = mutableListOf<Any>(bikeId)
        lastPerformedAt?.let {
            sql.append(" AND started_at > ?")
            params.add(java.sql.Timestamp.from(it))
        }
        return jdbc.queryForObject(sql.toString(), { rs, _ ->
            SinceLast(rs.getLong("dist"), rs.getTimestamp("first_tour")?.toInstant())
        }, *params.toTypedArray())
    }

    /**
     * SUM(tour.distance) for the bike in the half-open interval
     * (fromExclusive, toInclusive]; either bound may be null (unbounded on
     * that side). Bounds are appended only when non-null, matching [sinceLast],
     * to avoid PG's "could not determine data type" on a bound JDBC null.
     */
    fun distanceBetween(bikeId: UUID, fromExclusive: Instant?, toInclusive: Instant?): Long {
        val sql = StringBuilder("SELECT COALESCE(SUM(distance), 0) AS dist FROM tour WHERE bike_id = ?")
        val params = mutableListOf<Any>(bikeId)
        fromExclusive?.let {
            sql.append(" AND started_at > ?")
            params.add(java.sql.Timestamp.from(it))
        }
        toInclusive?.let {
            sql.append(" AND started_at <= ?")
            params.add(java.sql.Timestamp.from(it))
        }
        return jdbc.queryForObject(sql.toString(), { rs, _ ->
            rs.getLong("dist")
        }, *params.toTypedArray())
    }
}
