package com.driveperf.feature.analysis

import app.cash.turbine.test
import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.AnalysisDataBuilder
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.replay.EventMarkers
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.presentation.analysis.AnalysisEffect
import com.driveperf.presentation.analysis.AnalysisPresenter
import com.driveperf.presentation.analysis.AnalysisSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Android wrapper only: the cursor as snapshot state, snapping, the map tap and the hand-off of every control to
 * the shared presenter. Window, zoom, persistence and analytics are the presenter's (AnalysisPresenterTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** A synthetic 200 s drive due north at 10 m/s, one braking event at 100 s. No real place. */
    private fun analysis(): TripAnalysis {
        val n = 200 * 50
        val trace = AnalysisTrace(
            50, DoubleArray(n) { 0.1 }, DoubleArray(n) { 0.0 }, DoubleArray(n), DoubleArray(n), IntArray(n),
            (0..200).map { AnalysisFix(it.toDouble(), 10.0, it * 0.00009, 0.0, null) },
        )
        val replay = Replay("t1", BoundingBox(0.0, 0.0, 0.018, 0.0), 200.0, emptyList(), false,
            (0..200).map { ReplayPoint(it.toDouble(), it * 0.00009, 0.0, 10.0) })
        val trip = Trip(
            id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.MANUAL, startedAt = 0,
            startTzOffsetMin = 0, durationS = 200.0, distanceM = 2_000.0, createdAt = 0, updatedAt = 0,
        )
        val event = DrivingEvent(
            "e1", "t1", EventType.BRAKE, 100.0, 99.0, 101.0, 2.0, -0.3, null, 0.009, 0.0, 12.0, 6.0, Confidence.HIGH, "v1",
        )
        val data = AnalysisDataBuilder.build(200.0, 2_000.0, trace, emptyList(), emptyList())
        return TripAnalysis(trip, replay, data, EventMarkers.build(listOf(event), emptyMap(), emptyList()), listOf(event), null)
    }

    private fun viewModel(found: TripAnalysis? = analysis()) = AnalysisViewModel { scope ->
        AnalysisPresenter("t1", AnalysisSource.TRIP_DETAIL, { found }, null, flowOf(UnitSystem.METRIC), null, scope, Dispatchers.Main)
    }

    @Test fun `a drag moves the cursor at once and commits it when the finger lifts`() {
        val vm = viewModel()
        assertNotNull(vm.state.value.timeline)
        assertNull(vm.drag(0.25, 0.01))
        assertEquals(50.0, vm.cursor.doubleValue, 1e-9)
        assertEquals(0.0, vm.state.value.cursorS, 0.0)
        vm.dragEnd()
        assertEquals(50.0, vm.state.value.cursorS, 1e-9)
    }

    @Test fun `a drag near a tick snaps to its peak and names the event`() {
        val vm = viewModel()
        assertEquals("e1", vm.drag(0.51, 0.02))
        assertEquals(100.0, vm.cursor.doubleValue, 1e-9)
    }

    @Test fun `tapping the map snaps to the nearest track point`() {
        val vm = viewModel()
        vm.tapMap(0.0, 1_500.0)
        assertEquals(150.0, vm.cursor.doubleValue, 1.0)
        assertEquals(vm.cursor.doubleValue, vm.state.value.cursorS, 1e-9)
    }

    @Test fun `controls reach the presenter`() {
        val vm = viewModel()
        vm.setAxis(AnalysisAxis.DISTANCE)
        assertEquals(AnalysisAxis.DISTANCE, vm.state.value.axis)
        vm.zoomIn()
        vm.zoomIn()
        vm.zoomOut()
        assertEquals(2.0, vm.state.value.zoom, 0.0)
        vm.pinch(2.0)
        assertEquals(4.0, vm.state.value.zoom, 0.0)
        vm.resetZoom()
        assertEquals(1.0, vm.state.value.zoom, 0.0)
        vm.toggle(AnalysisChannel.ELEVATION)
        assertTrue(AnalysisChannel.ELEVATION in vm.state.value.channels)
        vm.expandGg()
        assertTrue(vm.state.value.ggExpanded)
        vm.filterGg(EventType.BRAKE)
        assertEquals(EventType.BRAKE, vm.state.value.ggFilter)
        vm.showEvent("e1")
        assertFalse(vm.state.value.ggExpanded)
        assertEquals(100.0, vm.cursor.doubleValue, 1e-9)
        vm.expandGg()
        vm.collapseGg()
        assertFalse(vm.state.value.ggExpanded)
        vm.jumpTo(20.0)
        assertEquals(20.0, vm.state.value.cursorS, 1e-9)
    }

    @Test fun `back and replay are effects`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.openReplay()
            assertEquals(AnalysisEffect.OpenReplay("t1"), awaitItem())
            vm.back()
            assertEquals(AnalysisEffect.Close, awaitItem())
        }
    }

    @Test fun `with nothing loaded a drag or a map tap does nothing`() {
        val vm = viewModel(found = null)
        assertNull(vm.drag(0.5, 0.1))
        vm.tapMap(1.0, 1.0)
        assertEquals(0.0, vm.cursor.doubleValue, 0.0)
        AnalysisControls.None.drag(0.5, 0.1)
    }
}
