package de.cyclingsir.cetrack.report.storage

/**
 * Initially created on 2/3/23.
 */
/*

 @Repository
interface ReportRepository : CrudRepository<ReportProjection, Long>{

    @Query("""SELECT p.name AS partName, PT.name AS partType,
        PPT.validFrom AS validFrom , PPT.validUntil AS validUntil
        FROM part p
        LEFT JOIN part_part_types PPT ON  p.id = PPT.partId
        LEFT JOIN part_type PT ON PPT.partTypeId = PT.id
        WHERE PPT.validFrom <= '2022-08-15 00:00:00' AND (PPT.validUntil >= '2022-08-15 23:59:00' OR PPT.validUntil IS NULL)
    """)
    fun getReport() : Collection<ReportProjection>
}
*/
