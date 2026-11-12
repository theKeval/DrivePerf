package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.AutoDetectHealth
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.capture.WatchdogState
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeAutoDetectWatchdog
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.FixedTimeZone
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-CAP-14 through the engine: check-ins reach the watchdog port and the store, analytics reach the port. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderCheckInTest {
    private val clock = TestClock()
    private val location = FakeLocationSource()
    private val watchdog = FakeAutoDetectWatchdog()
    private val watchdogStore = InMemoryWatchdogStore()
    private val analytics = RecordingAnalytics()
    private val store = InMemoryTripStore(parking)
    private val hourMs = 3_600_000L

    private fun TestScope.engine(): TripRecorderEngine = TripRecorderEngine(
        CapturePorts(
            location, FakeMotionSource(), FakeActivitySource(), FakePowerState(), FakeParkedRegionMonitor(),
            InMemoryTripFileStore(), watchdog,
        ),
        CapturePersistence(store, InMemoryCaptureLog(), TripSummariser(), watchdog = watchdogStore),
        clock,
        FixedTimeZone("Etc/GMT+5", -300),
        backgroundScope,
        analytics = analytics,
    )

    @Test
    fun launchSchedulesTheWatchdogAndShowsTheCheckIn() = runTest {
        val engine = engine()
        val launchedAt = clock.wallMs()
        engine.onLaunch(RearmReason.BOOT)
        runCurrent()
        assertEquals(launchedAt + 24 * hourMs, watchdog.scheduledAt)
        assertEquals(WatchdogState(launchedAt, launchedAt + 24 * hourMs, null), watchdogStore.state)
        assertEquals(launchedAt, engine.status.value.lastCheckInWallMs)
        assertEquals(listOf(mapOf("reason" to "boot")), analytics.named("rearmed"))
    }

    @Test
    fun aLateCheckInReportsTheWatchdogAndHoldsTheNextOne() = runTest {
        val armedAt = clock.wallMs()
        watchdogStore.state = WatchdogState(armedAt, armedAt + 24 * hourMs, null)
        val engine = engine()
        clock.advanceMs(30 * hourMs)
        engine.onWake(WakeEvent(WakeKind.MOVE_SIGNAL, clock.monotonicNs(), clock.wallMs()))
        runCurrent()
        assertEquals(1, analytics.named("watchdog_fired").size)
        assertEquals(armedAt + 96 * hourMs, watchdog.scheduledAt, "72 h after the one that fired")
        engine.onWatchdogOpened()
        engine.onForeground()
        runCurrent()
        assertEquals(1, analytics.named("watchdog_opened").size)
        assertEquals(1, analytics.named("watchdog_fired").size, "an on-time check-in fires nothing")
    }

    @Test
    fun pausingRemovesTheWatchdogAndHealthFollowsTheTriggers() = runTest {
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        engine.setTriggersAvailable(false)
        runCurrent()
        assertEquals(AutoDetectHealth.NEEDS_ATTENTION, engine.status.value.autoDetectHealth)
        engine.setAutoDetect(false)
        runCurrent()
        assertNull(watchdog.scheduledAt)
        assertNull(watchdogStore.state.dueWallMs)
        assertEquals(AutoDetectHealth.OFF, engine.status.value.autoDetectHealth)
        engine.onForeground()
        runCurrent()
        assertNull(watchdog.scheduledAt, "no watchdog during a pause (F-CAP-15 AC2)")
        engine.setTriggersAvailable(true)
        engine.setAutoDetect(true)
        runCurrent()
        assertEquals(AutoDetectHealth.ON, engine.status.value.autoDetectHealth)
        assertEquals(clock.wallMs() + 24 * hourMs, watchdog.scheduledAt)
    }

    @Test
    fun aManualTripReportsItsStartEndAndFinaliseTime() = runTest {
        val engine = engine()
        engine.onLaunch()
        runCurrent()
        engine.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "widget"))
        runCurrent()
        var north = 0.0
        repeat(200) {
            clock.advanceS(1.0)
            advanceTimeBy(1000)
            north += 15.0
            location.emit(fix(clock.monotonicNs() / 1e9, 15.0, north))
            runCurrent()
        }
        engine.stop()
        runCurrent()
        assertEquals(listOf(mapOf("mode" to "manual", "trigger" to "widget")), analytics.named("trip_started"))
        assertEquals(listOf(mapOf("by" to "user")), analytics.named("trip_ended"))
        assertEquals(listOf(mapOf("bucket" to "lt_1s")), analytics.named("trip_finalise_ms"))
        assertTrue(analytics.named("rearmed").isNotEmpty())
    }
}
