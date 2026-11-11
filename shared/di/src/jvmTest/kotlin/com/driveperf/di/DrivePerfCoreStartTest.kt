package com.driveperf.di

import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DrivePerfCoreStartTest {
    private val clock = TestClock()
    private val bindings = object : PlatformBindings {
        val files = InMemoryTripFileStore()
        val location = FakeLocationSource()
        val analytics = RecordingAnalyticsSink()

        override fun clock() = clock

        override fun location() = location

        override fun motion() = FakeMotionSource()

        override fun activity() = FakeActivitySource()

        override fun power() = FakePowerState()

        override fun parkedRegion() = FakeParkedRegionMonitor()

        override fun tripFiles() = files

        override fun deviceId() = "device-1"

        override fun analyticsSink() = analytics
    }

    @AfterTest
    fun reset() = DrivePerfCore.reset()

    @Test
    fun servicesNeedStart() {
        assertFailsWith<IllegalStateException> { DrivePerfCore.services }
    }

    @Test
    fun startWiresAWorkingRecorderOnAnEmptyDatabase() = runBlocking {
        val core = DrivePerfCore.start(bindings)
        assertSame(core, DrivePerfCore.start(bindings))
        core.recorder.onLaunch()
        core.recorder.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        withTimeout(5_000) { core.recorder.status.first { it.state == RecorderState.RECORDING } }
        assertTrue(bindings.location.isRunning)
        assertEquals("device-1", bindings.deviceId())
        assertTrue(core.trips.observeTrips().first().isEmpty())
        assertTrue(core.routes.observeRoutes().first().isEmpty())
    }

    @Test
    fun aManualTripIsFinalisedThroughThePipelineWithItsR1File() = runBlocking {
        val core = DrivePerfCore.start(bindings)
        core.recorder.onLaunch()
        core.recorder.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        withTimeout(5_000) { core.recorder.status.first { it.state == RecorderState.RECORDING } }
        repeat(30) { i ->
            clock.advanceS(1.0)
            val t = clock.monotonicNs()
            bindings.location.emit(LocationSample(t, clock.wallMs(), 2.5 + i * 1e-4, -27.5, 10.0, 4.0, 3.0, 12.0, 0.3, 0.0, 3.0))
        }
        withTimeout(5_000) { core.recorder.status.first { it.distanceM > 0.0 } }
        core.recorder.stop()
        val trip = withTimeout(10_000) { core.trips.observeTrips().first { it.isNotEmpty() } }.single()
        assertEquals(TripSource.MANUAL, trip.source)
        val r1 = assertNotNull(bindings.files.describe(trip.id, TripFileKind.R1, withSha256 = false))
        assertTrue(r1.compressed, "R1 is gzip-compressed at finalisation")
        assertTrue(assertNotNull(bindings.files.describe(trip.id, TripFileKind.R0, withSha256 = false)).compressed)
    }

    @Test
    fun aScratchCoreIsSeparateAndUsesItsOwnTimers() = runBlocking {
        val scratch = DrivePerfCore.scratch(
            bindings, com.driveperf.data.local.JvmDatabaseDriverFactory(), kotlinx.coroutines.Dispatchers.IO,
            com.driveperf.pipeline.capture.CaptureConfig(candidateTimeoutS = 0.2),
        )
        assertFailsWith<IllegalStateException>("a scratch core never becomes the app's core") { DrivePerfCore.services }
        scratch.recorder.onLaunch()
        scratch.recorder.onWake(WakeEvent(WakeKind.MOVE_SIGNAL, clock.monotonicNs(), clock.wallMs(), null))
        withTimeout(5_000) { scratch.recorder.status.first { it.state == RecorderState.CANDIDATE } }
        withTimeout(5_000) { scratch.recorder.status.first { it.state == RecorderState.IDLE } }
        assertTrue(bindings.files.tripIds().isEmpty(), "the rejected candidate's samples are deleted")
        scratch.close()
    }

    @Test
    fun analyticsWaitsForTheUsageChoice() {
        val services = DrivePerfCore.start(bindings)
        val event = object : AnalyticsEvent {
            override val name = "onboarding_started"
            override val properties = emptyMap<String, String>()
        }

        services.analytics.track(event)
        assertTrue(bindings.analytics.calls.isEmpty())

        services.analytics.setUsageConsent(false)
        assertEquals(listOf("enabled=false"), bindings.analytics.calls)
    }
}

class RecordingAnalyticsSink : AnalyticsSink {
    val calls = mutableListOf<String>()

    override fun setCollectionEnabled(enabled: Boolean) {
        calls += "enabled=$enabled"
    }

    override fun log(event: AnalyticsEvent) {
        calls += "log:${event.name}"
    }

    override fun resetId() {
        calls += "reset"
    }
}
