package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.AutoDetectHealth
import com.driveperf.domain.capture.AutoDetectPause
import com.driveperf.domain.capture.AutoDetectPauseLength
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeAutoDetectResumeNotice
import com.driveperf.testing.FakeAutoDetectWatchdog
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.FixedTimeZone
import com.driveperf.testing.InMemoryAutoDetectPauseStore
import com.driveperf.testing.InMemoryCaptureLog
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.InMemoryTripStore
import com.driveperf.testing.InMemoryWatchdogStore
import com.driveperf.testing.RecordingAnalytics
import com.driveperf.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-CAP-15 through the engine: the pause, its end by timer or by the user, the watchdog and a restart (EC-RBT). */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderPauseTest {
    private val clock = TestClock()
    private val watchdog = FakeAutoDetectWatchdog()
    private val notice = FakeAutoDetectResumeNotice()
    private val region = FakeParkedRegionMonitor()
    private val pauses = InMemoryAutoDetectPauseStore()
    private val analytics = RecordingAnalytics()
    private val store = InMemoryTripStore(parking)
    private val hourMs = 3_600_000L

    private fun TestScope.engine(): TripRecorderEngine = TripRecorderEngine(
        CapturePorts(
            FakeLocationSource(), FakeMotionSource(), FakeActivitySource(), FakePowerState(), region,
            InMemoryTripFileStore(), watchdog, notice,
        ),
        CapturePersistence(store, InMemoryCaptureLog(), TripSummariser(), watchdog = InMemoryWatchdogStore(), pause = pauses),
        clock,
        FixedTimeZone("Etc/GMT+5", -300),
        backgroundScope,
        analytics = analytics,
    )

    private fun TripRecorderEngine.autoWake() {
        onWake(WakeEvent(WakeKind.PARKED_EXIT, clock.monotonicNs(), clock.wallMs()))
    }

    /** Moves the wall clock and the engine's timers together. */
    private fun TestScope.advanceHours(hours: Long) {
        clock.advanceMs(hours * hourMs)
        advanceTimeBy(hours * hourMs)
        runCurrent()
    }

    @Test
    fun aPauseStopsAutomaticTripsAndTheWatchdogButNotManualOnes() = runTest {
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        assertNotNull(watchdog.scheduledAt)
        val until = clock.wallMs() + hourMs
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, until)
        runCurrent()

        val status = engine.status.value
        assertEquals(AutoDetectHealth.PAUSED, status.autoDetectHealth)
        assertEquals(until, status.autoDetectPausedUntilWallMs)
        assertTrue(status.autoDetectEnabled, "the Automatic trips switch stays on")
        assertNull(watchdog.scheduledAt, "no watchdog during the pause (AC2)")
        assertEquals(until, notice.scheduledAt, "the resume notice is set for the end (AC1)")
        assertEquals(AutoDetectPause(AutoDetectPauseLength.ONE_HOUR, until - hourMs, until), pauses.pause)
        assertEquals(listOf(mapOf("duration" to "1h")), analytics.named("auto_paused"))

        engine.autoWake()
        engine.onForeground()
        runCurrent()
        assertEquals(RecorderState.IDLE, engine.status.value.state, "no automatic trip starts (AC1)")
        assertNull(watchdog.scheduledAt, "a wake or foreground during the pause is no check-in")

        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        runCurrent()
        assertEquals(RecorderState.RECORDING, engine.status.value.state, "Start still works")
        assertEquals(AutoDetectHealth.PAUSED, engine.status.value.autoDetectHealth)
    }

    @Test
    fun thePauseEndsOnTimeAndReArmsTheTriggers() = runTest {
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() + hourMs)
        runCurrent()
        region.disarm()
        val cancelsBefore = notice.cancels

        advanceHours(1)
        assertEquals(AutoDetectHealth.ON, engine.status.value.autoDetectHealth)
        assertNull(engine.status.value.autoDetectPausedUntilWallMs)
        assertEquals(parking.point, region.armed?.first, "the parked region is re-armed (AC2)")
        assertEquals(clock.wallMs() + 24 * hourMs, watchdog.scheduledAt, "a check-in schedules the watchdog again")
        assertEquals(listOf(mapOf("by" to "timer")), analytics.named("auto_resumed"))
        assertEquals(cancelsBefore, notice.cancels, "the notice that says so is not removed")
        assertNull(pauses.pause)

        engine.autoWake()
        runCurrent()
        assertEquals(RecorderState.CANDIDATE, engine.status.value.state, "automatic trips start again")
    }

    @Test
    fun aPauseSurvivesARebootAndEndsAtTheFirstWakeAfterIt() = runTest {
        val until = clock.wallMs() + 10 * hourMs
        pauses.pause = AutoDetectPause(AutoDetectPauseLength.UNTIL_MORNING, clock.wallMs(), until)

        val rebooted = engine()
        rebooted.onLaunch(RearmReason.BOOT)
        rebooted.autoWake()
        runCurrent()
        assertEquals(AutoDetectHealth.PAUSED, rebooted.status.value.autoDetectHealth, "EC-RBT")
        assertEquals(until, rebooted.status.value.autoDetectPausedUntilWallMs)
        assertEquals(RecorderState.IDLE, rebooted.status.value.state)
        assertNull(watchdog.scheduledAt, "a launch during the pause schedules no watchdog")
        assertEquals(until, notice.scheduledAt, "the notice is set again: Android alarms do not survive a reboot")

        // The process was suspended past the end (no timer ran): the next wake ends the pause, then starts a trip.
        clock.advanceMs(11 * hourMs)
        rebooted.autoWake()
        runCurrent()
        assertEquals(AutoDetectHealth.ON, rebooted.status.value.autoDetectHealth)
        assertEquals(RecorderState.CANDIDATE, rebooted.status.value.state)
        assertEquals(listOf(mapOf("by" to "timer")), analytics.named("auto_resumed"))
        assertNotNull(watchdog.scheduledAt)
    }

    @Test
    fun aPauseThatEndedWhileTheAppWasNotRunningEndsAtLaunch() = runTest {
        pauses.pause = AutoDetectPause(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() - 2 * hourMs, clock.wallMs() - hourMs)
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        assertEquals(AutoDetectHealth.ON, engine.status.value.autoDetectHealth)
        assertNull(pauses.pause)
        assertEquals(clock.wallMs() + 24 * hourMs, watchdog.scheduledAt)
        assertEquals(listOf(mapOf("by" to "timer")), analytics.named("auto_resumed"))
    }

    @Test
    fun theUserCanResumeOrUndoAndTheNoticeIsRemoved() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.pauseAutoDetect(AutoDetectPauseLength.UNTIL_MORNING, clock.wallMs() + 8 * hourMs)
        runCurrent()
        engine.resumeAutoDetect()
        runCurrent()
        assertEquals(AutoDetectHealth.ON, engine.status.value.autoDetectHealth)
        assertNull(notice.scheduledAt)
        assertNotNull(watchdog.scheduledAt)
        assertEquals(listOf(mapOf("by" to "user")), analytics.named("auto_resumed"))

        engine.pauseAutoDetect(AutoDetectPauseLength.UNTIL_RESUMED, null)
        runCurrent()
        assertEquals(AutoDetectHealth.PAUSED, engine.status.value.autoDetectHealth)
        assertNull(engine.status.value.autoDetectPausedUntilWallMs, "until I turn it back on")
        engine.undoPauseAutoDetect()
        runCurrent()
        assertEquals(AutoDetectHealth.ON, engine.status.value.autoDetectHealth)
        assertEquals(1, analytics.named("auto_pause_undone").size)
        assertEquals(1, analytics.named("auto_resumed").size, "Undo is not counted as a resume")
        engine.resumeAutoDetect()
        runCurrent()
        assertEquals(1, analytics.named("auto_resumed").size, "resuming when not paused does nothing")
    }

    @Test
    fun anIndefinitePauseHasNoEndAndSurvivesARestart() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.pauseAutoDetect(AutoDetectPauseLength.UNTIL_RESUMED, null)
        runCurrent()
        assertNull(notice.scheduledAt)
        assertEquals(listOf(mapOf("duration" to "indefinite")), analytics.named("auto_paused"))
        advanceHours(72)
        assertEquals(AutoDetectHealth.PAUSED, engine.status.value.autoDetectHealth)

        val restarted = engine()
        restarted.onLaunch()
        runCurrent()
        assertEquals(AutoDetectHealth.PAUSED, restarted.status.value.autoDetectHealth)
    }

    @Test
    fun turningAutomaticTripsOffEndsThePauseQuietly() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() + hourMs)
        runCurrent()
        engine.setAutoDetect(false)
        runCurrent()
        assertEquals(AutoDetectHealth.OFF, engine.status.value.autoDetectHealth)
        assertNull(pauses.pause)
        assertNull(notice.scheduledAt)
        assertTrue(analytics.named("auto_resumed").isEmpty())
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() + hourMs)
        runCurrent()
        assertNull(pauses.pause, "nothing to pause while automatic trips are off")
        engine.setAutoDetect(true)
        runCurrent()
        assertEquals(AutoDetectHealth.ON, engine.status.value.autoDetectHealth)
    }

    @Test
    fun pausingDropsAnAutomaticCandidateAndIgnoresAnEndInThePast() = runTest {
        val engine = engine()
        engine.onLaunch()
        engine.autoWake()
        runCurrent()
        assertEquals(RecorderState.CANDIDATE, engine.status.value.state)
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() - 1)
        runCurrent()
        assertEquals(RecorderState.CANDIDATE, engine.status.value.state, "an end already past is ignored")
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() + hourMs)
        runCurrent()
        assertEquals(RecorderState.IDLE, engine.status.value.state)
        assertTrue(store.recordingTrip() == null, "the candidate's rows are gone")
        assertTrue(analytics.named("auto_candidate").isEmpty(), "a pause is not a rejection")
    }

    @Test
    fun aPauseStoreThatCannotWriteStillPauses() = runTest {
        pauses.writable = false
        val engine = engine()
        engine.onLaunch()
        engine.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, clock.wallMs() + hourMs)
        engine.autoWake()
        runCurrent()
        assertEquals(AutoDetectHealth.PAUSED, engine.status.value.autoDetectHealth)
        assertEquals(RecorderState.IDLE, engine.status.value.state)
    }
}
