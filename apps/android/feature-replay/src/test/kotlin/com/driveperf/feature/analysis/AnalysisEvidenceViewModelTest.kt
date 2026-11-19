package com.driveperf.feature.analysis

import app.cash.turbine.test
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Ticks and G-G peaks open the evidence (DRIVE-39 AC1); a drag across a tick still only scrubs. */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisEvidenceViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** A synthetic 200 s drive due north at 10 m/s, braking at 100 s; [withEvent] false is after "Not accurate?". */
    private fun analysis(withEvent: Boolean = true): TripAnalysis {
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
        val events = listOf(
            DrivingEvent("e1", "t1", EventType.BRAKE, 100.0, 99.0, 101.0, 2.0, -0.3, null, 0.009, 0.0, 12.0, 6.0, Confidence.HIGH, "v1"),
        ).filter { withEvent }
        val data = AnalysisDataBuilder.build(200.0, 2_000.0, trace, emptyList(), emptyList())
        return TripAnalysis(trip, replay, data, EventMarkers.build(events, emptyMap(), emptyList()), events, null)
    }

    private var current = analysis()

    private fun viewModel() = AnalysisViewModel { scope ->
        AnalysisPresenter("t1", AnalysisSource.TRIP_DETAIL, { current }, null, flowOf(UnitSystem.METRIC), null, scope, Dispatchers.Main)
    }

    @Test fun `a tick or peak row opens the evidence and moves the drawn cursor to its peak`() = runTest {
        val vm = viewModel()
        vm.expandGg()
        vm.effects.test {
            vm.openEvidence("e1")
            assertEquals(AnalysisEffect.OpenEvidence("e1"), awaitItem())
        }
        assertFalse(vm.state.value.ggExpanded)
        assertEquals(100.0, vm.cursor.doubleValue, 1e-9)
    }

    @Test fun `dragging across a tick snaps to it without opening anything`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            assertEquals("e1", vm.drag(0.5, 0.02))
            vm.dragEnd()
            vm.openReplay()
            assertEquals(AnalysisEffect.OpenReplay("t1"), awaitItem())
        }
        assertEquals(100.0, vm.state.value.cursorS, 1e-9)
    }

    @Test fun `reload drops an event reported not accurate`() {
        val vm = viewModel()
        assertTrue(vm.state.value.timeline!!.ticks.any { it.eventId == "e1" })
        current = analysis(withEvent = false)
        vm.reload()
        assertTrue(vm.state.value.timeline!!.ticks.isEmpty())
        AnalysisControls.None.openEvidence("e1")
    }
}
