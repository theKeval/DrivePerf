package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.TripStatus
import com.driveperf.pipeline.capture.CaptureFixtures.activity
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeCaptureDeadlineAlarm
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.FixedTimeZone
import com.driveperf.testing.InMemoryCaptureLog
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.InMemoryTripStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DRIVE-223 end to end. Deep sleep is simulated by moving the recorder's clock (Android `elapsedRealtimeNanos`, which
 * counts in sleep) without moving coroutine time (`delay`, which does not), as on the OnePlus 7T field drive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderOverdueStopTest {
    private val clock = TestClock()
    private val location = FakeLocationSource()
    private val activitySource = FakeActivitySource()
    private val store = InMemoryTripStore(parking)
    private val alarm = FakeCaptureDeadlineAlarm()

    private fun TestScope.engine(): TripRecorderEngine = TripRecorderEngine(
        CapturePorts(
            location, FakeMotionSource(), activitySource, FakePowerState(), FakeParkedRegionMonitor(), InMemoryTripFileStore(),
            stopAlarm = alarm,
        ),
        CapturePersistence(store, InMemoryCaptureLog(), TripFinaliser { TripSummariser().finalise(it) }),
        clock,
        FixedTimeZone("Etc/GMT+5", -300),
        backgroundScope,
    )

    private fun nowS(): Double = clock.monotonicNs() / 1e9

    /** One fix per second for [seconds] at [speed], with the phone awake (both clocks move). */
    private fun TestScope.drive(seconds: Int, speed: Double, northStart: Double): Double {
        var north = northStart
        repeat(seconds) {
            clock.advanceS(1.0)
            advanceTimeBy(1000)
            north += speed
            location.emit(fix(nowS(), speed, north))
            runCurrent()
        }
        return north
    }

    /** A 150 s, 1.5 km automatic drive, then a stop: paused, with the stop at [stopAtS]. */
    private fun TestScope.driveAndPark(engine: TripRecorderEngine): Double {
        engine.onLaunch()
        runCurrent()
        clock.advanceS(100.0)
        activitySource.history = listOf(activity(nowS() - 30, ActivityType.AUTOMOTIVE))
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        val north = drive(150, 10.0, 0.0)
        assertEquals(RecorderState.RECORDING, engine.status.value.state)
        drive(40, 0.0, north)
        assertEquals(RecorderState.PAUSED, engine.status.value.state)
        return north
    }

    private val stopAtS = 100.0 + 151

    private fun TestScope.sleep(seconds: Double) {
        clock.advanceS(seconds)
        runCurrent()
    }

    @Test
    fun aStalledStopTimerNoLongerMergesTheNextDriveIntoTheParkedTrip() = runTest {
        val engine = engine()
        val north = driveAndPark(engine)
        val firstId = engine.status.value.tripId
        sleep(2 * 3600.0 + 11 * 60)
        assertEquals(RecorderState.PAUSED, engine.status.value.state, "the stop timer stalled in deep sleep")

        engine.onWake(WakeEvent(WakeKind.ACTIVITY_VEHICLE, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        val first = store.finalised.single().trip
        assertEquals(firstId, first.id)
        assertEquals(TripStatus.FINALISED, first.status)
        assertEquals(clock.wallMs() - ((nowS() - stopAtS) * 1000).toLong(), first.endedAt, "it ends where the car parked")
        assertEquals(RecorderState.CANDIDATE, engine.status.value.state)
        val secondId = engine.status.value.tripId
        assertNotEquals(firstId, secondId)

        drive(150, 10.0, north)
        assertEquals(RecorderState.RECORDING, engine.status.value.state)
        assertEquals(secondId, engine.status.value.tripId, "the second drive is a trip of its own")
        assertEquals(1, store.finalised.size)
    }

    @Test
    fun theDeviceWakeEndsTheTripNearItsStopTimeWhileTheTimerIsStalled() = runTest {
        val engine = engine()
        driveAndPark(engine)
        assertEquals(clock.monotonicNs() - (nowS() - stopAtS - 240).times(1e9).toLong(), alarm.scheduledAtMonoNs)

        sleep(215.0)
        alarm.fire()
        runCurrent()
        assertEquals(RecorderState.IDLE, engine.status.value.state)
        val trip = store.finalised.single().trip
        assertEquals(clock.wallMs() - ((nowS() - stopAtS) * 1000).toLong(), trip.endedAt)
        assertNull(alarm.scheduledAtMonoNs)
    }

    @Test
    fun aPullAwayBeforeTheDeadlineCancelsTheDeviceWake() = runTest {
        val engine = engine()
        val north = driveAndPark(engine)
        assertNotNull(alarm.scheduledAtMonoNs)
        drive(5, 8.0, north)
        assertEquals(RecorderState.RECORDING, engine.status.value.state)
        assertNull(alarm.scheduledAtMonoNs)
        assertTrue(alarm.cancels >= 1)
        assertTrue(store.finalised.isEmpty())
    }
}
