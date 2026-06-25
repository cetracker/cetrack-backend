package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.bike.storage.BikeRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.support.MySQLContainerIT
import de.cyclingsir.cetrack.tour.storage.ImportIgnoreRepository
import de.cyclingsir.cetrack.tour.storage.ImportSessionRepository
import de.cyclingsir.cetrack.tour.storage.ImportStateRepository
import de.cyclingsir.cetrack.tour.storage.TourEntity
import de.cyclingsir.cetrack.tour.storage.TourRepository
import de.cyclingsir.cetrack.tour.support.DerbyFixtureBuilder
import de.cyclingsir.cetrack.tour.support.TourSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/**
 * End-to-end integration test for the MyTourbook Derby DB import.
 * Drives the real service against a MySQL Testcontainers DB.
 * Each group is independent; state carries forward within a group via real commits.
 *
 * Fixture mt_tour_id scheme: 8000000000001–8000000000099
 * (disjoint from ImportConstraintIT's 9000000000001 and TourConstraintIT's mt-001/mt-002)
 *
 * F1 tour IDs + triples (startMs, distance, durationMoving):
 *   BIKE_A tours: 8000000000001..5  →  triples T1..T5
 *   BIKE_B tours: 8000000000011..15 →  triples T11..T15
 *
 * F2 re-imports (same triple, new mt_tour_id): 8000000000021..23 match T1, T2, T3
 * F2 new tours:                                8000000000024..25  (new triples)
 * F5 tour: 8000000000031  — triple == T1 (a BIKE_A triple), tagged BIKE_B
 * F_multi: 8000000000041  — triple == T1 (matches two existing tours in Group F precondition)
 * F4 ambiguous: 8000000000051  — tagged BIKE_A + BIKE_B
 * F_drift / F_incompat reuse F1 specs with different dbVersion / omitTable
 */
@Tag("import-integration")
class MyTourbookImportIT : MySQLContainerIT() {

    @Autowired private lateinit var importService: MyTourbookImportService
    @Autowired private lateinit var tourRepository: TourRepository
    @Autowired private lateinit var sessionRepository: ImportSessionRepository
    @Autowired private lateinit var ignoreRepository: ImportIgnoreRepository
    @Autowired private lateinit var stateRepository: ImportStateRepository
    @Autowired private lateinit var bikeRepository: BikeRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        val BIKE_A: UUID = UUID.fromString("a1111111-0001-0001-0001-000000000001")
        val BIKE_B: UUID = UUID.fromString("b2222222-0002-0002-0002-000000000002")
        const val DB_VERSION_BASELINE = 59

        // F1 BIKE_A tours
        private val F1_A = listOf(
            TourSpec(8000000000001L, listOf(BIKE_A), 1_700_000_000_000L, 50_000, 6_600L),
            TourSpec(8000000000002L, listOf(BIKE_A), 1_700_100_000_000L, 55_000, 7_200L),
            TourSpec(8000000000003L, listOf(BIKE_A), 1_700_200_000_000L, 60_000, 7_800L),
            TourSpec(8000000000004L, listOf(BIKE_A), 1_700_300_000_000L, 45_000, 5_700L),
            TourSpec(8000000000005L, listOf(BIKE_A), 1_700_400_000_000L, 70_000, 9_000L),
        )
        // F1 BIKE_B tours
        private val F1_B = listOf(
            TourSpec(8000000000011L, listOf(BIKE_B), 1_700_500_000_000L, 52_000, 6_900L),
            TourSpec(8000000000012L, listOf(BIKE_B), 1_700_600_000_000L, 58_000, 7_500L),
            TourSpec(8000000000013L, listOf(BIKE_B), 1_700_700_000_000L, 63_000, 8_100L),
            TourSpec(8000000000014L, listOf(BIKE_B), 1_700_800_000_000L, 44_000, 5_400L),
            TourSpec(8000000000015L, listOf(BIKE_B), 1_700_900_000_000L, 75_000, 9_600L),
        )
        val F1_SPECS = F1_A + F1_B

        // F2: F1 tours (same mtTourIds, filtered post-commit) + 3 re-imports (new id, same triple as first 3 F1_A) + 2 new
        private val F2_REIMPORTS = listOf(
            TourSpec(8000000000021L, listOf(BIKE_A), F1_A[0].startTimestampMs, F1_A[0].distance, F1_A[0].durationMoving),
            TourSpec(8000000000022L, listOf(BIKE_A), F1_A[1].startTimestampMs, F1_A[1].distance, F1_A[1].durationMoving),
            TourSpec(8000000000023L, listOf(BIKE_B), F1_B[0].startTimestampMs, F1_B[0].distance, F1_B[0].durationMoving),
        )
        private val F2_NEW = listOf(
            TourSpec(8000000000024L, listOf(BIKE_A), 1_701_000_000_000L, 80_000, 10_200L),
            TourSpec(8000000000025L, listOf(BIKE_B), 1_701_100_000_000L, 65_000, 8_400L),
        )
        val F2_SPECS = F1_SPECS + F2_REIMPORTS + F2_NEW

        // F3: all committed tour ids (filtered) + only invalid/untracked tours (excluded by adapter)
        private val BIKE_C = UUID.fromString("c3333333-0003-0003-0003-000000000003")
        private val F3_INVALID = listOf(
            TourSpec(8000000000061L, listOf(BIKE_C), 1_701_200_000_000L, 40_000, 5_100L),   // foreign UUID
            TourSpec(8000000000062L, emptyList(),    1_701_300_000_000L, 41_000, 5_200L),   // untagged
            TourSpec(8000000000063L, listOf(BIKE_A), 1_701_400_000_000L, 42_000, 5_300L, person = 4), // wrong person
        )
        // F3: committed F1 + F2_NEW (all in DB → filtered) + invalid tours → yields 0 importable
        // Excludes F2_REIMPORTS (021-023): those are re-import candidates, not committed base tours
        val F3_SPECS = F1_SPECS + F2_NEW + F3_INVALID

        // F4: F3 base + 1 ambiguous tour tagged BIKE_A + BIKE_B
        val F4_SPECS = F3_SPECS + listOf(
            TourSpec(8000000000051L, listOf(BIKE_A, BIKE_B), 1_701_500_000_000L, 48_000, 6_000L)
        )

        // F5: 1 tour with triple == F1_A[0] but tagged BIKE_B
        val F5_SPECS = listOf(
            TourSpec(8000000000031L, listOf(BIKE_B), F1_A[0].startTimestampMs, F1_A[0].distance, F1_A[0].durationMoving)
        )

        // F_multi: 1 tour whose triple matches two existing tours (BIKE_A + BIKE_B same triple)
        val F_MULTI_SPECS = listOf(
            TourSpec(8000000000041L, listOf(BIKE_A), 1_702_000_000_000L, 55_000, 7_000L)
        )
    }

    @BeforeEach
    fun reset() {
        tourRepository.deleteAll()
        sessionRepository.deleteAll()
        ignoreRepository.deleteAll()
        // Idempotent bike upsert via JDBC to avoid Hibernate 6 merge-of-non-existent-entity issue
        // (BikeEntity uses @GeneratedValue which causes merge() to throw StaleObjectStateException
        //  when the entity has a pre-set UUID but no corresponding row yet)
        jdbcTemplate.update(
            "INSERT INTO bike (id, model, manufacturer, created_at) VALUES (UNHEX(?), ?, '', NOW(6)) ON DUPLICATE KEY UPDATE model=model",
            BIKE_A.toString().replace("-", ""), "Bike A"
        )
        jdbcTemplate.update(
            "INSERT INTO bike (id, model, manufacturer, created_at) VALUES (UNHEX(?), ?, '', NOW(6)) ON DUPLICATE KEY UPDATE model=model",
            BIKE_B.toString().replace("-", ""), "Bike B"
        )
        // Reset import_state baseline version
        jdbcTemplate.update("UPDATE import_state SET last_db_version=?, updated_at=NOW(6) WHERE id=1", DB_VERSION_BASELINE)
    }

    @AfterEach
    fun cleanup() {
        tourRepository.deleteAll()
        sessionRepository.deleteAll()
        ignoreRepository.deleteAll()
    }

    // ─── Group A — Clean initial import ─────────────────────────────────────────

    @Test
    fun `Group A - clean initial import with supersession`() {
        // A1: Upload F1 → PENDING, 10 candidates, 0 warnings
        val sessionA1 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F1_SPECS))!!
        assertThat(sessionA1.status).isEqualTo("PENDING")
        assertThat(sessionA1.candidates).hasSize(10)
        assertThat(sessionA1.warnings).isEmpty()

        // A2: Commit 5 candidates → 5 tours; session COMMITTED
        val first5 = sessionA1.candidates.take(5).map { it.MTTOURID }
        importService.commit(sessionA1.sessionId, first5)
        assertThat(tourRepository.count()).isEqualTo(5)
        assertThat(sessionRepository.findById(sessionA1.sessionId).get().status).isEqualTo("COMMITTED")

        // A3: Upload F1 again → PENDING, 5 candidates (committed ones filtered), 0 warnings
        val sessionA3 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F1_SPECS))!!
        assertThat(sessionA3.status).isEqualTo("PENDING")
        assertThat(sessionA3.candidates).hasSize(5)
        assertThat(sessionA3.warnings).isEmpty()

        // A4: Upload F1 again without committing A3 → A3 SUPERSEDED; new PENDING with same 5
        val sessionA4 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F1_SPECS))!!
        assertThat(sessionA4.status).isEqualTo("PENDING")
        assertThat(sessionA4.candidates).hasSize(5)
        assertThat(sessionRepository.findById(sessionA3.sessionId).get().status).isEqualTo("SUPERSEDED")

        // A5: Commit all 5 remaining → 10 tours total; COMMITTED
        val last5 = sessionA4.candidates.map { it.MTTOURID }
        importService.commit(sessionA4.sessionId, last5)
        assertThat(tourRepository.count()).isEqualTo(10)
        assertThat(sessionRepository.findById(sessionA4.sessionId).get().status).isEqualTo("COMMITTED")
    }

    // ─── Group B — LOGICAL_DUPLICATE resolution ──────────────────────────────────

    @Test
    fun `Group B - logical duplicate SUPPRESS and REPLACE`() {
        // Precondition: all 10 F1 tours committed
        seedF1Tours()

        // B1: Upload F2 → 2 new candidates (F2_NEW), 3 LOGICAL_DUPLICATE warnings (F2_REIMPORTS)
        val sessionB1 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F2_SPECS))!!
        assertThat(sessionB1.status).isEqualTo("PENDING")
        assertThat(sessionB1.candidates).hasSize(2)
        val dups = sessionB1.warnings.filter { it.type == "LOGICAL_DUPLICATE" }
        assertThat(dups).hasSize(3)
        assertThat(dups.none { it.replaceDisabled }).isTrue()

        // B2: SUPPRESS warning[0], REPLACE warnings[1]+[2], import both new candidates
        val suppressed = dups[0]
        val replaced1 = dups[1]
        val replaced2 = dups[2]
        val newCandidateIds = sessionB1.candidates.map { it.MTTOURID }
        importService.commit(
            sessionB1.sessionId,
            newCandidateIds,
            listOf(
                WarningResolutionRequest(suppressed.mtTourId!!, "SUPPRESS"),
                WarningResolutionRequest(replaced1.mtTourId!!, "REPLACE"),
                WarningResolutionRequest(replaced2.mtTourId!!, "REPLACE"),
            )
        )
        // 1 suppress ignore row, 2 replaced (mt_tour_id updated + updatedAt), 2 new added = 12 total
        assertThat(tourRepository.count()).isEqualTo(12)
        assertThat(ignoreRepository.count()).isEqualTo(1)
        val r1 = tourRepository.findAll().first { it.mtTourId == replaced1.mtTourId }
        assertThat(r1.updatedAt).isNotNull()
        val r2 = tourRepository.findAll().first { it.mtTourId == replaced2.mtTourId }
        assertThat(r2.updatedAt).isNotNull()
        assertThat(sessionRepository.findById(sessionB1.sessionId).get().status).isEqualTo("COMMITTED")

        // B3: Upload F2 again → REPLACE freed old mtTourIds (002/011), which re-appear as new
        // LOGICAL_DUPLICATE warnings; suppressed tour (021) is silently skipped (triple in ignore)
        val sessionB3 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F2_SPECS))!!
        val dups3 = sessionB3.warnings.filter { it.type == "LOGICAL_DUPLICATE" }
        assertThat(dups3).hasSize(2)
        // 021 must NOT appear as a warning — it is silently skipped because its triple is in the ignore set
        assertThat(dups3.none { it.mtTourId == suppressed.mtTourId }).isTrue()
        assertThat(sessionB3.candidates).isEmpty()
        assertThat(tourRepository.count()).isEqualTo(12)
    }

    // ─── Group C — IMPORT_NEW for same triple, different bike ────────────────────

    @Test
    fun `Group C - IMPORT_NEW for same triple different bike`() {
        // Precondition: F1_A[0] committed as BIKE_A
        seedTour(F1_A[0])

        // C1: Upload F5 → 0 candidates, 1 LOGICAL_DUPLICATE (F5 triple == F1_A[0], but tagged BIKE_B)
        val sessionC1 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F5_SPECS))!!
        assertThat(sessionC1.candidates).isEmpty()
        val dupWarning = sessionC1.warnings.single { it.type == "LOGICAL_DUPLICATE" }
        assertThat(dupWarning.replaceDisabled).isFalse()

        // C2: Commit IMPORT_NEW → new BIKE_B tour + ignore row; COMMITTED
        importService.commit(
            sessionC1.sessionId,
            emptyList(),
            listOf(WarningResolutionRequest(dupWarning.mtTourId!!, "IMPORT_NEW"))
        )
        assertThat(tourRepository.count()).isEqualTo(2)
        assertThat(ignoreRepository.count()).isEqualTo(1)
        assertThat(sessionRepository.findById(sessionC1.sessionId).get().status).isEqualTo("COMMITTED")
        val newTour = tourRepository.findAll().first { it.mtTourId == F5_SPECS[0].mtTourId.toString() }
        assertThat(newTour.bike?.id).isEqualTo(BIKE_B)

        // C3: Upload F5 again → triple in ignore set; incoming mt_tour_id now in DB → stage() null
        val sessionC3 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F5_SPECS))
        assertThat(sessionC3).isNull()
        assertThat(sessionRepository.findAllByStatus("PENDING")).isEmpty()
    }

    // ─── Group D — All tours invalid / already imported ──────────────────────────

    @Test
    fun `Group D - all tours invalid yields null session`() {
        // Precondition: all F2 base tours in DB; F3 has only those + invalid tours
        seedF1Tours()
        // seed F2 new tours as well
        seedTour(F2_NEW[0])
        seedTour(F2_NEW[1])

        val sessionCountBefore = sessionRepository.count()
        // D1: Upload F3 → all mtTourIds already in DB; invalid tours excluded → stage() null
        val sessionD1 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F3_SPECS))
        assertThat(sessionD1).isNull()
        assertThat(sessionRepository.count()).isEqualTo(sessionCountBefore)
    }

    // ─── Group E — AMBIGUOUS_BIKE ────────────────────────────────────────────────

    @Test
    fun `Group E - AMBIGUOUS_BIKE warning committed with no candidates`() {
        // Precondition: Group D complete; F4 = F3 + 1 ambiguous tour
        seedF1Tours()
        seedTour(F2_NEW[0])
        seedTour(F2_NEW[1])

        val tourCountBefore = tourRepository.count()

        // E1: Upload F4 → 0 candidates, 1 AMBIGUOUS_BIKE warning
        val sessionE1 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F4_SPECS))!!
        assertThat(sessionE1.candidates).isEmpty()
        assertThat(sessionE1.warnings.single { it.type == "AMBIGUOUS_BIKE" }).isNotNull()

        // E2: Commit with no candidates, no resolution → COMMITTED; tour count unchanged
        importService.commit(sessionE1.sessionId, emptyList())
        assertThat(sessionRepository.findById(sessionE1.sessionId).get().status).isEqualTo("COMMITTED")
        assertThat(tourRepository.count()).isEqualTo(tourCountBefore)
    }

    // ─── Group F — Multi-match LOGICAL_DUPLICATE (edge case) ─────────────────────

    @Test
    fun `Group F - replaceDisabled true when triple matches two tours`() {
        // Precondition: two tours sharing the same triple but different bikes
        val sharedTriple = F_MULTI_SPECS[0]
        val tourA = seedTourWithBike(
            TourSpec(8000000000091L, listOf(BIKE_A), sharedTriple.startTimestampMs, sharedTriple.distance, sharedTriple.durationMoving),
            BIKE_A
        )
        val tourB = seedTourWithBike(
            TourSpec(8000000000092L, listOf(BIKE_B), sharedTriple.startTimestampMs, sharedTriple.distance, sharedTriple.durationMoving),
            BIKE_B
        )
        assertThat(tourA).isNotNull()
        assertThat(tourB).isNotNull()

        // F1: Upload F_multi → 1 LOGICAL_DUPLICATE with replaceDisabled = true
        val sessionF1 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F_MULTI_SPECS))!!
        val dupWarning = sessionF1.warnings.single { it.type == "LOGICAL_DUPLICATE" }
        assertThat(dupWarning.replaceDisabled).isTrue()

        // F2: Commit REPLACE → rejected (ServiceException IMPORT_RESOLUTION_REPLACE_AMBIGUOUS)
        val ex = assertThrows<ServiceException> {
            importService.commit(
                sessionF1.sessionId,
                emptyList(),
                listOf(WarningResolutionRequest(dupWarning.mtTourId!!, "REPLACE"))
            )
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.IMPORT_RESOLUTION_REPLACE_AMBIGUOUS)

        // re-stage (session was in same tx that threw, so it's still PENDING in practice;
        // but we need a fresh PENDING session after the exception)
        val sessionF3 = importService.stage(DerbyFixtureBuilder.buildFixture(tours = F_MULTI_SPECS))!!
        val dup2 = sessionF3.warnings.single { it.type == "LOGICAL_DUPLICATE" }

        // F3: SUPPRESS → ignore row written; COMMITTED
        importService.commit(
            sessionF3.sessionId,
            emptyList(),
            listOf(WarningResolutionRequest(dup2.mtTourId!!, "SUPPRESS"))
        )
        assertThat(ignoreRepository.count()).isEqualTo(1)
        assertThat(sessionRepository.findById(sessionF3.sessionId).get().status).isEqualTo("COMMITTED")
    }

    // ─── Group G — Schema drift / incompatible ───────────────────────────────────

    @Test
    fun `Group G1 - hasDrift true when DBVERSION differs from baseline`() {
        val sessionG1 = importService.stage(
            DerbyFixtureBuilder.buildFixture(dbVersion = DB_VERSION_BASELINE + 1, tours = F1_SPECS)
        )!!
        assertThat(sessionG1.hasDrift).isTrue()
        assertThat(sessionG1.candidates).isNotEmpty()
    }

    @Test
    fun `Group G2 - DERBY_SCHEMA_INCOMPATIBLE when required table missing`() {
        val sessionCountBefore = sessionRepository.count()
        val ex = assertThrows<ServiceException> {
            importService.stage(
                DerbyFixtureBuilder.buildFixture(tours = F1_SPECS, omitTable = "TOURTAG")
            )
        }
        assertThat(ex.getError()).isEqualTo(ErrorCodesDomain.DERBY_SCHEMA_INCOMPATIBLE)
        assertThat(sessionRepository.count()).isEqualTo(sessionCountBefore)
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun seedF1Tours() {
        F1_SPECS.forEach { spec -> seedTour(spec) }
    }

    private fun seedTour(spec: TourSpec) {
        val bikeEntity = spec.bikeTags.firstOrNull()?.let { bikeRepository.findById(it).orElse(null) }
        tourRepository.save(
            TourEntity(
                id = null,
                mtTourId = spec.mtTourId.toString(),
                title = spec.title,
                distance = spec.distance,
                durationMoving = spec.durationMoving,
                startedAt = Instant.ofEpochMilli(spec.startTimestampMs),
                startYear = Instant.ofEpochMilli(spec.startTimestampMs).atZone(java.time.ZoneOffset.UTC).year.toShort(),
                startMonth = Instant.ofEpochMilli(spec.startTimestampMs).atZone(java.time.ZoneOffset.UTC).monthValue.toShort(),
                startDay = Instant.ofEpochMilli(spec.startTimestampMs).atZone(java.time.ZoneOffset.UTC).dayOfMonth.toShort(),
                altUp = spec.altUp,
                altDown = spec.altDown,
                powerTotal = spec.powerTotal,
                bike = bikeEntity
            )
        )
    }

    private fun seedTourWithBike(spec: TourSpec, bikeId: UUID): TourEntity {
        val bikeEntity = bikeRepository.findById(bikeId).orElseThrow()
        return tourRepository.save(
            TourEntity(
                id = null,
                mtTourId = spec.mtTourId.toString(),
                title = spec.title,
                distance = spec.distance,
                durationMoving = spec.durationMoving,
                startedAt = Instant.ofEpochMilli(spec.startTimestampMs),
                startYear = Instant.ofEpochMilli(spec.startTimestampMs).atZone(java.time.ZoneOffset.UTC).year.toShort(),
                startMonth = Instant.ofEpochMilli(spec.startTimestampMs).atZone(java.time.ZoneOffset.UTC).monthValue.toShort(),
                startDay = Instant.ofEpochMilli(spec.startTimestampMs).atZone(java.time.ZoneOffset.UTC).dayOfMonth.toShort(),
                altUp = spec.altUp,
                altDown = spec.altDown,
                powerTotal = spec.powerTotal,
                bike = bikeEntity
            )
        )
    }
}
