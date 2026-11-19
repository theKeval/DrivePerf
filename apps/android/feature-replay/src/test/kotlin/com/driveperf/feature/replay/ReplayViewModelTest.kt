package com.driveperf.feature.replay

import app.cash.turbine.test
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.presentation.replay.ReplayEffect
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * The Android wrapper only: the frame clock, the playhead as snapshot state, the legend, the renderer choice and
 * Try again. Playback rules, scrub-resume and analytics are the shared presenter's (ReplayPresenterTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayViewModelTest {
    private val analytics = RecordingAnalytics()
    private val repository = FakeReplayRepository()
    private val online = MutableStateFlow(true)
    private var nowNs = 1_000_000_000L
    private var recording = false

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(hasKey: Boolean = true, units: MutableStateFlow<UnitSystem> = MutableStateFlow(UnitSystem.METRIC)) =
        ReplayViewModel(
            presenterFactory = { scope ->
                ReplayPresenter("t1", ReplaySource.TRIP_DETAIL, repository, { recording }, analytics, { nowNs }, scope)
            },
            maps = ReplayMapConfig(hasKey, null, online),
            units = units,
        )

    private val ReplayViewModel.ready: ReplayUiState.Ready get() = state.value as ReplayUiState.Ready

    private fun ReplayViewModel.frameAfter(seconds: Double) {
        nowNs += (seconds * 1e9).toLong()
        onFrame()
    }

    @Test fun `frames move the playhead on the presenter's clock, and pause holds it`() {
        val vm = viewModel()
        assertFalse(vm.ready.playing)
        vm.togglePlay()
        assertTrue(vm.ready.playing)
        vm.frameAfter(1.0)
        assertEquals("Auto plays the whole trip in about 60 s", TestReplays.DURATION / 60, vm.time.doubleValue, 1e-6)
        vm.togglePlay()
        vm.frameAfter(5.0)
        assertFalse(vm.ready.playing)
        assertEquals(TestReplays.DURATION / 60, vm.time.doubleValue, 1e-6)
    }

    @Test fun `speed and scrubbing reach the playhead in the same call`() {
        val vm = viewModel()
        vm.setSpeed(PlaybackSpeed.X10)
        assertEquals(PlaybackSpeed.X10, vm.ready.speed)
        vm.scrubStart()
        vm.scrubTo(300.0)
        assertEquals(300.0, vm.time.doubleValue, 0.0)
        assertTrue(vm.ready.scrubbing)
        vm.scrubEnd()
        assertFalse(vm.ready.scrubbing)
    }

    @Test fun `the end is sent to the presenter once, when the playhead reaches it`() {
        val vm = viewModel()
        vm.togglePlay()
        vm.frameAfter(30.0)
        assertTrue("half way, still playing", vm.ready.playing)
        vm.frameAfter(40.0)
        assertFalse(vm.ready.playing)
        assertEquals(TestReplays.DURATION, vm.time.doubleValue, 0.0)
        vm.frameAfter(1.0)
        assertEquals(1, analytics.names.count { it == "replay_completed" })
    }

    @Test fun `analytics come from the shared presenter only, each once`() {
        val vm = viewModel()
        vm.togglePlay()
        vm.scrubStart()
        vm.scrubTo(10.0)
        vm.scrubEnd()
        vm.openLegend()
        vm.openLegend()
        assertEquals(
            listOf("replay_opened", "replay_played", "replay_scrubbed", "quality_overlay_legend_opened"),
            analytics.names,
        )
    }

    @Test fun `the legend opens and closes`() {
        val vm = viewModel()
        vm.openLegend()
        assertTrue(vm.ready.legendOpen)
        vm.closeLegend()
        assertFalse(vm.ready.legendOpen)
    }

    @Test fun `without a Maps key Replay draws the plain canvas, with no offline note while online`() {
        val vm = viewModel(hasKey = false)
        assertEquals(MapRenderer.CANVAS, vm.ready.renderer)
        online.value = false
        assertEquals(MapRenderer.CANVAS_OFFLINE, vm.ready.renderer)
    }

    @Test fun `with a key it uses Google Maps, and falls back offline or when tiles never load`() {
        val vm = viewModel(hasKey = true)
        assertEquals(MapRenderer.GOOGLE, vm.ready.renderer)
        online.value = false
        assertEquals(MapRenderer.CANVAS_OFFLINE, vm.ready.renderer)
        online.value = true
        assertEquals(MapRenderer.GOOGLE, vm.ready.renderer)
        vm.onMapTilesFailed()
        assertEquals(MapRenderer.CANVAS_OFFLINE, vm.ready.renderer)
    }

    @Test fun `units follow the display setting`() {
        val units = MutableStateFlow(UnitSystem.METRIC)
        val vm = viewModel(units = units)
        units.value = UnitSystem.IMPERIAL
        assertEquals(UnitSystem.IMPERIAL, vm.ready.units)
    }

    @Test fun `a missing trip shows the error, and Try again loads it with a fresh presenter`() {
        repository.result = { null }
        val vm = viewModel()
        assertEquals(ReplayUiState.Error, vm.state.value)
        repository.result = { TestReplays.trip() }
        vm.retry()
        assertTrue(vm.state.value is ReplayUiState.Ready)
        vm.togglePlay()
        assertTrue("the new presenter takes the controls", vm.ready.playing)
        assertEquals(2, repository.calls)
    }

    @Test fun `a recording trip asks for the drive screen`() = runTest {
        recording = true
        val vm = viewModel()
        vm.effects.test { assertEquals(ReplayEffect.ShowDriveScreen, awaitItem()) }
        assertEquals(ReplayUiState.Loading, vm.state.value)
        assertEquals(0, repository.calls)
    }
}
