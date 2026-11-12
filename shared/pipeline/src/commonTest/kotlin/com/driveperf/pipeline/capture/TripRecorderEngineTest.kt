package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.AutoDetectHealth
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.pipeline.capture.CaptureFixtures.activity
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.motionRun
import com.driveperf.pipeline.capture.CaptureFixtures.ns
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
import com.driveperf.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderEngineTest {
    private val clock = TestClock()
    private val location = FakeLocationSource()
    private val motion = FakeMotionSource()
    private val activitySource = FakeActivitySource()
    private val power = FakePowerState()
    private val region = FakeParkedRegionMonitor()
    private val files = InMemoryTripFileStore()
    private val store = InMemoryTripStore(parking)
    private val log = InMemoryCaptureLog()

    /** Every capture handed to the finaliser. */
    private val finalised = mutableListOf<CapturedTrip>()
    private val finaliser = TripFinaliser { request ->
        finalised += request.capture
        TripSummariser().finalise(request)
    }

    private fun TestScope.engine(): TripRecorderEngine = TripRecorderEngine(
        CapturePorts(location, motion, activitySource, power, region, files),
        CapturePersistence(store, log, finaliser),
        clock,
        FixedTimeZone("Etc/GMT+5", -300),
        backgroundScope,
    )

    private fun nowS(): Double = clock.monotonicNs() / 1e9

    /** One fix per second for [seconds] at [speed], advancing both clocks. Returns the final northing. */
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

    private fun manualWake(detail: String = "app") =
        WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), detail)

    @Test
    fun aManualTripIsRecordedFromStartToStopAndCommitted() = runTest {
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        assertEquals(parking.point, region.armed?.first)

        clock.advanceS(10.0)
        engine.onWake(manualWake("control_center"))
        runCurrent()
        val status = engine.status.value
        assertEquals(RecorderState.RECORDING, status.state)
        assertTrue(status.isManual)
        assertTrue(location.isRunning)
        assertEquals(50, motion.rateHz)
        assertEquals(TripStatus.RECORDING, store.trips.values.single().status)

        motion.emit(*motionRun(nowS(), 1.0, noisy = true).toTypedArray())
        drive(200, 15.0, 0.0)
        assertEquals(2985.0, engine.status.value.distanceM, 5.0)
        assertTrue(log.syncs > 0, "R0 is synced at most every 5 s")

        engine.stop()
        runCurrent()
        val committed = store.finalised.single().trip
        assertEquals(TripStatus.FINALISED, committed.status)
        assertEquals(TripSource.MANUAL, committed.source)
        assertEquals("control_center", committed.sourceDetail)
        assertEquals(200.0, committed.durationS!!, 0.01)
        assertEquals(RecorderState.IDLE, engine.status.value.state)
        assertFalse(location.isRunning)
        assertNull(motion.rateHz)
        assertNotNull(store.parking)
        assertEquals(store.parking!!.point, region.armed?.first)
    }

    @Test
    fun aShortAutomaticTripEndsByTheStopTimerAndIsDiscarded() = runTest {
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        clock.advanceS(100.0)
        activitySource.history = listOf(activity(nowS() - 30, ActivityType.AUTOMOTIVE))
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        assertEquals(RecorderState.CANDIDATE, engine.status.value.state)

        val north = drive(15, 8.0, 0.0)
        assertEquals(RecorderState.RECORDING, engine.status.value.state)
        drive(40, 0.0, north)
        assertEquals(RecorderState.PAUSED, engine.status.value.state)

        clock.advanceS(240.0)
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RecorderState.IDLE, engine.status.value.state)
        assertTrue(store.trips.isEmpty(), "trips under 500 m are discarded (F-CAP-17)")
        assertTrue(log.tripIds().isEmpty())
        assertEquals(store.parking!!.point, region.armed?.first)
    }

    @Test
    fun theCandidateTimeoutDiscardsAFalseWake() = runTest {
        val engine = engine()
        clock.advanceS(10.0)
        engine.onWake(WakeEvent(WakeKind.MOVE_SIGNAL, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        clock.advanceS(180.0)
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(RecorderState.IDLE, engine.status.value.state)
        assertTrue(store.trips.isEmpty())
        assertFalse(location.isRunning)
    }

    @Test
    fun confirmationBackfillsRecordedAccelerometerIntoR0() = runTest {
        motion.recorded = listOf(RecordedAccelSample(ns(1.0), Vec3(0.0, 0.0, 1.0)))
        val engine = engine()
        engine.onLaunch()
        clock.advanceS(100.0)
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        drive(22, 8.0, 0.0)
        val tripId = store.trips.keys.single()
        assertEquals(1, log.read(tripId)!!.recordedAccel.size)
        assertTrue(activitySource.queries.single().first >= parking.wallMs)
    }

    @Test
    fun aTripKilledMomentsAgoResumesOnLaunch() = runTest {
        val h = header(0.0, WakeKind.PARKED_EXIT, id = "killed")
        store.insertRecording(recordingTrip(h.tripId))
        log.open(h)
        log.append(h.tripId, CaptureBatch(fixes = listOf(fix(100.0, 12.0))))
        clock.advanceS(150.0)
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        assertEquals("killed", engine.status.value.tripId)
        assertEquals(RecorderState.CANDIDATE, engine.status.value.state)
        assertTrue(location.isRunning)
    }

    @Test
    fun aTripKilledLongAgoIsFinalisedAsInterrupted() = runTest {
        val h = header(0.0, WakeKind.MANUAL, id = "old")
        store.insertRecording(recordingTrip(h.tripId, TripSource.MANUAL))
        log.open(h)
        log.append(h.tripId, CaptureBatch(fixes = (1..300).map { fix(it.toDouble(), 10.0, it * 10.0) }))
        clock.advanceS(5000.0)
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        assertEquals(TripStatus.INTERRUPTED, store.finalised.single().trip.status)
        assertEquals(RecorderState.IDLE, engine.status.value.state)
    }

    @Test
    fun aRecordingRowWithoutALogIsRemovedAtLaunch() = runTest {
        store.insertRecording(recordingTrip("orphan"))
        engine().onLaunch()
        runCurrent()
        assertTrue(store.trips.isEmpty())
    }

    @Test
    fun passengerAndAutoDetectControlsReachTheMachine() = runTest {
        val engine = engine()
        engine.setAutoDetect(false)
        runCurrent()
        assertFalse(engine.status.value.autoDetectEnabled)
        assertEquals(AutoDetectHealth.OFF, engine.status.value.autoDetectHealth)
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        assertEquals(RecorderState.IDLE, engine.status.value.state)

        engine.onWake(manualWake())
        runCurrent()
        drive(5, 10.0, 0.0)
        engine.markPassenger(keep = false)
        runCurrent()
        assertTrue(store.trips.isEmpty())
        assertEquals(RecorderState.IDLE, engine.status.value.state)
    }

    @Test
    fun aKeptPassengerTripIsCommittedAsPassenger() = runTest {
        val engine = engine()
        engine.onWake(manualWake())
        runCurrent()
        drive(150, 10.0, 0.0)
        engine.markPassenger(keep = true)
        runCurrent()
        assertEquals(TripKind.PASSENGER, store.finalised.single().trip.kind)
    }

    @Test
    fun aFullDiskStopsImuCaptureButKeepsGnss() = runTest {
        files.freeBytes = 1
        val engine = engine()
        engine.onWake(manualWake())
        runCurrent()
        assertNull(motion.rateHz)
        assertTrue(location.isRunning)
    }

    @Test
    fun aTripFileThatCannotBeCreatedDropsTheStartAndSaysSo() = runTest {
        log.openable = false
        val engine = engine()
        engine.onWake(manualWake())
        runCurrent()
        val status = engine.status.value
        assertEquals(RecorderState.IDLE, status.state)
        assertTrue(status.startFailed)
        assertTrue(store.trips.isEmpty())
        assertFalse(location.isRunning)
        assertNull(motion.rateHz)

        log.openable = true
        engine.onWake(manualWake())
        runCurrent()
        assertEquals(RecorderState.RECORDING, engine.status.value.state)
        assertFalse(engine.status.value.startFailed)
    }

    @Test
    fun aBackfillStillRunningAtStopIsInR0BeforeFinalisation() = runTest {
        motion.recorded = listOf(RecordedAccelSample(ns(1.0), Vec3(0.0, 0.0, 1.0)))
        motion.holdRecorded = true
        val engine = engine()
        engine.onLaunch()
        clock.advanceS(100.0)
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        drive(150, 8.0, 0.0)
        engine.stop()
        runCurrent()
        assertTrue(finalised.isEmpty(), "finalisation waits for the running query")

        motion.releaseRecorded()
        runCurrent()
        assertEquals(1, finalised.single().recordedAccel.size)
        assertEquals(TripStatus.FINALISED, store.finalised.single().trip.status)
    }

    @Test
    fun aBackfillThatNeverAnswersDelaysFinalisationOnlyBriefly() = runTest {
        motion.recorded = listOf(RecordedAccelSample(ns(1.0), Vec3(0.0, 0.0, 1.0)))
        motion.holdRecorded = true
        val engine = engine()
        engine.onLaunch()
        clock.advanceS(100.0)
        engine.onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        drive(150, 8.0, 0.0)
        engine.stop()
        advanceTimeBy(10_001)
        runCurrent()
        assertTrue(finalised.single().recordedAccel.isEmpty())
        val tripId = store.finalised.single().trip.id

        motion.releaseRecorded()
        runCurrent()
        assertTrue(log.read(tripId)!!.recordedAccel.isEmpty(), "a late answer is not appended to a finalised log")
    }

    private fun recordingTrip(id: String, source: TripSource = TripSource.AUTO) = Trip(
        id = id, status = TripStatus.RECORDING, kind = TripKind.DRIVER, source = source,
        startedAt = CaptureFixtures.WALL0, startTzOffsetMin = -300,
        createdAt = CaptureFixtures.WALL0, updatedAt = CaptureFixtures.WALL0,
    )
}
