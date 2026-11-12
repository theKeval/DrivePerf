package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripQuality
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.FixedTimeZone
import com.driveperf.testing.InMemoryCaptureLog
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.InMemoryTripStore
import com.driveperf.testing.RecordingAnalytics
import com.driveperf.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-CAP-16 AC2–3 and F-CAP-05 through the engine: the IMU port, the batching hint, `start_backfilled`, EC-RBT. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderBackoffTest {
    private val clock = TestClock()
    private val location = FakeLocationSource()
    private val motion = FakeMotionSource()
    private val analytics = RecordingAnalytics()
    private val store = InMemoryTripStore(parking)
    private val log = InMemoryCaptureLog()

    /** The summary finaliser, with an estimated start of [estimatedStartM] on the trip. */
    private var estimatedStartM: Double? = null
    private val finaliser = TripFinaliser { request ->
        val result = TripSummariser().finalise(request)
        val quality = TripQuality(
            QualityBadge.GOOD, null, Placement.UNKNOWN, 50.0, 100.0, null, estimatedStartM, 0.0, 0.0, 0.0, emptyList(),
        )
        result.copy(trips = result.trips.map { it.copy(trip = it.trip.copy(quality = quality)) })
    }

    private fun TestScope.engine(): TripRecorderEngine = TripRecorderEngine(
        CapturePorts(location, motion, FakeActivitySource(), FakePowerState(), FakeParkedRegionMonitor(), InMemoryTripFileStore()),
        CapturePersistence(store, log, finaliser),
        clock,
        FixedTimeZone("Etc/GMT+5", -300),
        backgroundScope,
        analytics = analytics,
    )

    private fun TestScope.drive(seconds: Int, speed: Double) {
        repeat(seconds) {
            clock.advanceS(1.0)
            advanceTimeBy(1000)
            location.emit(fix(clock.monotonicNs() / 1e9, speed, clock.monotonicNs() / 1e9 * speed))
            runCurrent()
        }
    }

    @Test
    fun steadyCruiseLowersTheImuAndAllowsBatchingUntilTheDriveScreenShows() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        drive(30, 22.0)
        assertFalse(engine.status.value.mayBatchLocation)
        assertEquals(50, motion.rateHz)
        drive(40, 22.0)
        assertEquals(25, motion.rateHz)
        assertTrue(engine.status.value.mayBatchLocation)
        assertEquals(listOf(mapOf("to" to "25", "reason" to "cruise")), analytics.named("imu_rate_changed"))

        engine.setInDriveScreenVisible(true)
        runCurrent()
        assertFalse(engine.status.value.mayBatchLocation, "never batched while the in-drive screen shows")
        engine.setInDriveScreenVisible(false)
        drive(1, 22.0)
        assertTrue(engine.status.value.mayBatchLocation)
        drive(3, 10.0)
        assertFalse(engine.status.value.mayBatchLocation)
        assertEquals(50, motion.rateHz)
    }

    @Test
    fun anEstimatedStartIsReportedWithItsImuBackfill() = runTest {
        estimatedStartM = 420.0
        val engine = engine()
        engine.onLaunch()
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        val id = engine.status.value.tripId!!
        log.append(id, com.driveperf.domain.capture.CaptureBatch(recordedAccel = listOf(RecordedAccelSample(0L, Vec3(0.0, 0.0, 1.0)))))
        drive(200, 15.0)
        engine.stop()
        runCurrent()
        assertEquals(listOf(mapOf("gap_m_bucket" to "250_500", "imu_backfill" to "true")), analytics.named("start_backfilled"))
    }

    @Test
    fun noEstimateMeansNoBackfillEvent() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        drive(200, 15.0)
        engine.stop()
        runCurrent()
        assertTrue(analytics.named("start_backfilled").isEmpty())
    }

    @Test
    fun afterARebootTheNextTripStartsWithoutAParkingSpot() = runTest {
        val engine = engine()
        engine.onLaunch(RearmReason.BOOT)
        runCurrent()
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        val id = engine.status.value.tripId!!
        assertNull(log.read(id)?.header?.parking, "EC-RBT: no estimate and no parking bound")
        assertFalse(store.parkingTrusted)
    }

    @Test
    fun aRestartAfterTheRebootStillDistrustsTheOldSpotUntilATripEnds() = runTest {
        store.parkingTrusted = false
        val engine = engine()
        engine.onLaunch()
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        assertNull(log.read(engine.status.value.tripId!!)?.header?.parking)
        drive(200, 15.0)
        engine.stop()
        runCurrent()
        assertTrue(store.parkingTrusted, "the trip end recorded a new, trusted spot")
    }

    @Test
    fun theDriveScreenShownBeforeTheTripStartsKeepsLocationUnbatched() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.setInDriveScreenVisible(true)
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        drive(70, 22.0)
        assertEquals(25, motion.rateHz)
        assertFalse(engine.status.value.mayBatchLocation)
    }
}
