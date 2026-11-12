package com.driveperf.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripStatus
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-CAP-06 AC4: no trip is lost on a schema migration, tested with a fixture from every prior schema version. For each
 * `databases/N.db` (the schema as version N shipped it) the test loads `migrations/vN.sql` (rows as version N wrote
 * them), opens the file through the app's own driver path, which migrates it to the current version, and reads the
 * trips back through the repositories. `verifySqlDelightMigration` checks the schema; this checks the data.
 */
class MigrationTest {
    private val schemaDir = File("src/commonMain/sqldelight/databases")

    private fun versions(): List<Int> =
        schemaDir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".db").toIntOrNull() }.sorted()

    @Test
    fun everySchemaVersionHasAFixtureAndTheLatestIsCurrent() {
        val versions = versions()
        assertEquals((1..DrivePerfDatabase.Schema.version.toInt()).toList(), versions, "one N.db per schema version")
        versions.forEach { assertNotNull(fixture(it), "migrations/v$it.sql is missing") }
    }

    @Test
    fun everyVersionMigratesWithoutLosingTrips() = runTest {
        for (version in versions()) {
            val db = migrated(version)
            val repo = LocalTripRepository(db, InMemoryTripFileStore(), Dispatchers.Unconfined)
            val p = "v$version-"

            val listed = repo.observeTrips().first()
            assertEquals(listOf("${p}finalised", "${p}interrupted", "${p}suspect"), listed.map { it.id }, "v$version list")

            val finalised = assertNotNull(repo.trip("${p}finalised"))
            assertEquals(TripStatus.FINALISED, finalised.status)
            assertEquals(38_950.0, finalised.distanceM)
            assertTrue(finalised.startEstimated)
            val quality = assertNotNull(finalised.quality)
            assertEquals(QualityBadge.USABLE, quality.badge)
            assertEquals(listOf(SegmentReason.ESTIMATED_START, SegmentReason.NO_GNSS), quality.spans.map { it.reason })
            assertEquals(85, repo.score("${p}finalised")?.smoothness)
            assertEquals(listOf("${p}event"), repo.events("${p}finalised").map { it.id })
            assertTrue(repo.feedback("${p}finalised").getValue("${p}event").needed)
            assertEquals(1, db.tripQueries.filesForTrip("${p}finalised").executeAsList().size)

            val interrupted = assertNotNull(repo.trip("${p}interrupted"))
            assertEquals(TripStatus.INTERRUPTED, interrupted.status)
            assertEquals(InterruptionReason.APP_KILLED, interrupted.interrupted?.reason)
            assertEquals(SegmentReason.APP_KILLED, interrupted.quality?.spans?.single()?.reason)

            val suspect = assertNotNull(repo.trip("${p}suspect"))
            assertEquals(Suspect.TRANSIT, suspect.suspect)
            assertNull(suspect.correction)
            assertNull(suspect.correctedAt)

            val store = LocalTripStore(db, "device", Dispatchers.Unconfined)
            assertEquals("${p}recording", store.recordingTrip()?.id, "a trip recording during the update resumes")
            assertNotNull(store.lastParking())
            assertEquals(version < 4, store.isParkingTrusted(), "v$version: only the v4 fixture stored a reboot")
        }
    }

    @Test
    fun correctionsWrittenBySchema2SurviveLaterMigrations() = runTest {
        val db = migrated(2)
        val trips = LocalTripRepository(db, InMemoryTripFileStore(), Dispatchers.Unconfined)
        val bike = LocalTripCorrections(db, trips, Dispatchers.Unconfined, TestClock()).observeNotDrives().first().single()
        assertEquals("v2-bike", bike.id)
        assertEquals(1_762_102_000_000L, bike.correctedAt)
    }

    /** A copy of `N.db` with `vN.sql` loaded, opened through [JvmDatabaseDriverFactory] (which runs the migrations). */
    private fun migrated(version: Int): DrivePerfDatabase {
        val file = Files.createTempFile("driveperf-v$version-", ".db").toFile().apply { deleteOnExit() }
        File(schemaDir, "$version.db").copyTo(file, overwrite = true)
        val url = "jdbc:sqlite:${file.absolutePath}"
        JdbcSqliteDriver(url).use { raw ->
            checkNotNull(fixture(version)).split(";\n").map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { raw.execute(null, it, 0) }
            raw.execute(null, "PRAGMA user_version = $version", 0)
        }
        return openDatabase(JvmDatabaseDriverFactory(url).create())
    }

    private fun fixture(version: Int): String? =
        javaClass.getResource("/migrations/v$version.sql")?.readText()?.lines()
            ?.filterNot { it.startsWith("--") }?.joinToString("\n")
}
