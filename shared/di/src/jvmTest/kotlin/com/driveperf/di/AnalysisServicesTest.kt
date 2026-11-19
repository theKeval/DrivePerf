package com.driveperf.di

import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.analytics.NoOpAnalyticsSink
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.presentation.analysis.AnalysisSource
import com.driveperf.presentation.analysis.AnalysisUnavailable
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** DRIVE-45: the Analysis presenter, built through `CoreServices`, loads a real finalised trip and its R1 file. */
class AnalysisServicesTest {
    private val clock = TestClock()
    private val bindings = object : PlatformBindings {
        val location = FakeLocationSource()
        private val files = InMemoryTripFileStore()

        override fun clock() = clock

        override fun location() = location

        override fun motion() = FakeMotionSource()

        override fun activity() = FakeActivitySource()

        override fun power() = FakePowerState()

        override fun parkedRegion() = FakeParkedRegionMonitor()

        override fun tripFiles() = files

        override fun deviceId() = "device-1"

        override fun analyticsSink(): AnalyticsSink = NoOpAnalyticsSink
    }

    @AfterTest
    fun reset() = DrivePerfCore.reset()

    @Test
    fun aFinalisedTripOpensInAnalysis() = runBlocking {
        val core = DrivePerfCore.start(bindings)
        core.recorder.onLaunch()
        core.recorder.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        withTimeout(5_000) { core.recorder.status.first { it.state == RecorderState.RECORDING } }
        repeat(30) { i ->
            clock.advanceS(1.0)
            bindings.location.emit(LocationSample(clock.monotonicNs(), clock.wallMs(), 2.5 + i * 1e-4, -27.5, 10.0, 4.0, 3.0, 12.0, 0.3, 0.0, 3.0))
        }
        withTimeout(5_000) { core.recorder.status.first { it.distanceM > 0.0 } }
        core.recorder.stop()
        val trip = withTimeout(10_000) { core.trips.observeTrips().first { it.isNotEmpty() } }.single()

        val scope = CoroutineScope(Dispatchers.Default)
        val presenter = core.analysisPresenter(trip.id, AnalysisSource.TRIP_DETAIL, "CA", scope)
        val loaded = withTimeout(5_000) { presenter.state.first { !it.loading } }
        assertFalse(loaded.summaryOnly)
        val timeline = assertNotNull(loaded.timeline)
        assertEquals(AnalysisChannel.DEFAULTS.size, timeline.lanes.size)
        assertTrue(timeline.lanes.first { it.channel == AnalysisChannel.SPEED }.path!!.size > 0)
        assertNotNull(loaded.map)
        presenter.close()

        val missing = core.analysisPresenter("missing", scope)
        assertEquals(AnalysisUnavailable.NOT_FOUND, withTimeout(5_000) { missing.state.first { !it.loading } }.unavailable)
        missing.close()
        scope.cancel()
    }
}
