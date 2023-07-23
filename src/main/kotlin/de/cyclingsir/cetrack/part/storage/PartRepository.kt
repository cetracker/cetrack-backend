package de.cyclingsir.cetrack.part.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PartRepository : JpaRepository<PartEntity, UUID> {

/*
    @Query("""SELECT p.name AS partName, PT.name AS partType,
        PPT.validFrom AS validFrom , PPT.validUntil AS validUntil
        FROM part p
        LEFT JOIN part_part_types PPT ON  p.id = PPT.partId
        LEFT JOIN part_type PT ON PPT.partTypeId = PT.id
        WHERE PPT.validFrom <= '2022-08-15 00:00:00' AND (PPT.validUntil >= '2022-08-15 23:59:00' OR PPT.validUntil IS NULL)
    """)
    fun getReport() : Collection<ReportProjection>
*/

    /*
    SELECT p.name, CONCAT(format(sum(t.`length`)/100, 2, 'de_DE'), ' km') as "km total", SEC_TO_TIME(sum(t.duration)) as "Moved total"
FROM `part` p
LEFT JOIN `part_part_types` ppt ON  p.id = ppt.part_id
LEFT JOIN `part_type` pt ON ppt.part_type_id = pt.id
LEFT JOIN `bike` b ON  pt.bike_id = b.id
INNER JOIN `tour` t ON  t.bike_id = b.id AND ppt.valid_from <= t.started_at AND (t.started_at <= ppt.valid_until OR ppt.valid_until IS NULL)
GROUP BY p.name
ORDER BY p.name  DESC
     */
    @Query("""
        SELECT p.name AS partName, sum(t.distance) AS meterTotal, sum(t.durationMoving) AS secondsTotal,
        sum(t.altUp) AS altUpTotal, sum(t.altDown) AS altDownTotal, sum(t.powerTotal) as powerTotal
        FROM part p
        LEFT JOIN part_part_types ppt ON  p.id = ppt.partId
        LEFT JOIN part_type pt ON ppt.partTypeId = pt.id
        LEFT JOIN bike b ON  pt.bike.id = b.id
        INNER JOIN tour t ON  t.bike.id = b.id AND ppt.validFrom <= t.startedAt AND (t.startedAt <= ppt.validUntil OR ppt.validUntil IS NULL) 
        GROUP BY p.name
    """)
    fun getCompleteReport() : Collection<ReportProjectionComplete>
}
