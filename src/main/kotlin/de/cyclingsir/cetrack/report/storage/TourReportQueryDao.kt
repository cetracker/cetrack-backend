package de.cyclingsir.cetrack.report.storage

import de.cyclingsir.cetrack.report.domain.TourGranularity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * Flat per-(bucket, bike) row backing CE-0127's tour report; folded into
 * buckets by ReportService. start_year/start_month are nullable smallint in
 * the DB (V1.0) despite the non-null entity fields - the IS NOT NULL guards
 * keep legacy/hand-inserted NULL rows out of buckets and availableYears.
 */
data class TourAggregateRow(
    val year: Int,
    val month: Int?,
    val bikeId: UUID?,
    val bikeName: String?,
    val bikeModel: String?,
    val distance: Long,
    val ascent: Long,
    val durationMoving: Long
)

@Repository
class TourReportQueryDao(private val jdbc: JdbcTemplate) {

    fun tourAggregates(granularity: TourGranularity, fromYear: Int, toYear: Int): List<TourAggregateRow> {
        val sql = if (granularity == TourGranularity.MONTH) {
            """SELECT t.start_year, t.start_month, t.bike_id, b.name AS bike_name, b.model AS bike_model,
                      COALESCE(SUM(t.distance), 0) AS distance,
                      COALESCE(SUM(t.ascent), 0) AS ascent,
                      COALESCE(SUM(t.duration_moving), 0) AS duration_moving
               FROM tour t
               LEFT JOIN bike b ON b.id = t.bike_id
               WHERE t.start_year IS NOT NULL AND t.start_month IS NOT NULL
                 AND t.start_year BETWEEN ? AND ?
               GROUP BY t.start_year, t.start_month, t.bike_id, b.name, b.model
               ORDER BY t.start_year, t.start_month, b.name NULLS LAST, b.model"""
        } else {
            """SELECT t.start_year, t.bike_id, b.name AS bike_name, b.model AS bike_model,
                      COALESCE(SUM(t.distance), 0) AS distance,
                      COALESCE(SUM(t.ascent), 0) AS ascent,
                      COALESCE(SUM(t.duration_moving), 0) AS duration_moving
               FROM tour t
               LEFT JOIN bike b ON b.id = t.bike_id
               WHERE t.start_year IS NOT NULL
                 AND t.start_year BETWEEN ? AND ?
               GROUP BY t.start_year, t.bike_id, b.name, b.model
               ORDER BY t.start_year, b.name NULLS LAST, b.model"""
        }
        return jdbc.query(sql, { rs, _ -> row(rs, granularity) }, fromYear, toYear)
    }

    fun availableYears(): List<Int> =
        jdbc.query(
            "SELECT DISTINCT start_year FROM tour WHERE start_year IS NOT NULL ORDER BY start_year DESC"
        ) { rs, _ -> rs.getInt("start_year") }

    private fun row(rs: ResultSet, granularity: TourGranularity) = TourAggregateRow(
        year = rs.getInt("start_year"),
        month = if (granularity == TourGranularity.MONTH) rs.getInt("start_month") else null,
        bikeId = rs.getObject("bike_id", UUID::class.java),
        bikeName = rs.getString("bike_name"),
        bikeModel = rs.getString("bike_model"),
        distance = rs.getLong("distance"),
        ascent = rs.getLong("ascent"),
        durationMoving = rs.getLong("duration_moving")
    )
}
