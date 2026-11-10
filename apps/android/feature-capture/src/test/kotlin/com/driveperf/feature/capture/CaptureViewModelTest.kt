package com.driveperf.feature.capture

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private class FakeController(canRecord: Boolean = true) : CaptureController {
        override val snapshot = MutableStateFlow<CaptureSnapshot>(CaptureSnapshot.Idle)
        override val canRecord: StateFlow<Boolean> = MutableStateFlow(canRecord)
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    private var nowMs = 100_000L
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test
    fun `idle with location is ready`() = runTest(dispatcher) {
        val vm = CaptureViewModel(FakeController(), { nowMs })
        vm.state.test {
            assertEquals(CaptureUiState.Ready, expectMostRecentItem())
        }
    }

    @Test
    fun `idle without location says so instead of offering Start`() = runTest(dispatcher) {
        val vm = CaptureViewModel(FakeController(canRecord = false), { nowMs })
        vm.state.test {
            advanceTimeBy(1)
            assertEquals(CaptureUiState.NeedsLocation, expectMostRecentItem())
        }
    }

    @Test
    fun `recording shows elapsed time and distance`() = runTest(dispatcher) {
        val controller = FakeController()
        val vm = CaptureViewModel(controller, { nowMs })
        controller.snapshot.value = CaptureSnapshot.Recording(startedWallMs = 40_000, distanceM = 12_400.0, gps = GpsHealth.GOOD)
        vm.state.test {
            advanceTimeBy(1)
            val state = expectMostRecentItem() as CaptureUiState.Recording
            assertEquals("1:00", state.elapsed)
            assertEquals("12.4", state.distanceKm)
            assertEquals(GpsHealth.GOOD, state.gps)
        }
    }

    @Test
    fun `elapsed time advances every second without new fixes`() = runTest(dispatcher) {
        val controller = FakeController()
        val vm = CaptureViewModel(controller, { nowMs }, tickMs = 1_000)
        controller.snapshot.value = CaptureSnapshot.Recording(startedWallMs = 100_000, distanceM = 0.0, gps = GpsHealth.SEARCHING)
        vm.state.test {
            advanceTimeBy(1)
            assertEquals("0:00", (expectMostRecentItem() as CaptureUiState.Recording).elapsed)
            nowMs += 5_000
            advanceTimeBy(1_001)
            assertEquals("0:05", (expectMostRecentItem() as CaptureUiState.Recording).elapsed)
        }
    }

    @Test
    fun `Start asks the controller to start`() = runTest(dispatcher) {
        val controller = FakeController()
        CaptureViewModel(controller, { nowMs }).onStart()
        assertEquals(1, controller.starts)
    }

    @Test
    fun `Stop needs a confirmation and a dismissed one does nothing`() = runTest(dispatcher) {
        val controller = FakeController()
        val vm = CaptureViewModel(controller, { nowMs })
        controller.snapshot.value = CaptureSnapshot.Recording(0, 0.0, GpsHealth.GOOD)
        vm.state.test {
            advanceTimeBy(1)
            expectMostRecentItem()
            vm.onStopRequested()
            advanceTimeBy(1)
            assertTrue((expectMostRecentItem() as CaptureUiState.Recording).confirmingStop)
            assertEquals("a tap alone never stops the drive", 0, controller.stops)
            vm.onStopDismissed()
            advanceTimeBy(1)
            assertEquals(false, (expectMostRecentItem() as CaptureUiState.Recording).confirmingStop)
            assertEquals(0, controller.stops)
        }
    }

    @Test
    fun `a confirmed Stop stops the drive and clears the dialog`() = runTest(dispatcher) {
        val controller = FakeController()
        val vm = CaptureViewModel(controller, { nowMs })
        vm.onStopRequested()
        vm.onStopConfirmed()
        assertEquals(1, controller.stops)
    }

    @Test
    fun `a failed capture is reported`() = runTest(dispatcher) {
        val controller = FakeController()
        val vm = CaptureViewModel(controller, { nowMs })
        controller.snapshot.value = CaptureSnapshot.Failed
        vm.state.test {
            advanceTimeBy(1)
            assertEquals(CaptureUiState.Failed, expectMostRecentItem())
        }
    }
}
