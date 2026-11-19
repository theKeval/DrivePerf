package com.driveperf.feature.replay

import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.settings.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayViewModelTest {
    private val analytics = RecordingAnalytics()
    private val repository = FakeReplayRepository()
    private val online = MutableStateFlow(true)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        hasKey: Boolean = true,
        lock: ReplayProLock = ReplayProLock.NotBuilt,
        units: MutableStateFlow<UnitSystem> = MutableStateFlow(UnitSystem.METRIC),
    ) = ReplayViewModel(ReplayRequest("t1"), repository, analytics, ReplayMapConfig(hasKey, null, online), proLock = lock, units = units)

    private val ReplayViewModel.ready: ReplayUiState.Ready get() = state.value as ReplayUiState.Ready

    @Test fun `opens paused at the start, on Auto, and reports where it was opened from`() {
        val vm = viewModel()
        assertFalse(vm.ready.playing)
        assertEquals(PlaybackSpeed.AUTO, vm.ready.speed)
        assertEquals(0.0, vm.time.doubleValue, 0.0)
        assertEquals("replay_opened", analytics.events.first().name)
        assertEquals(mapOf("source" to "trip_detail"), analytics.events.first().properties)
    }

    @Test fun `play moves the marker at the Auto rate, and pause stops it in place`() {
        val vm = viewModel()
        vm.advance(1.0)
        assertEquals("paused: frames do nothing", 0.0, vm.time.doubleValue, 0.0)
        vm.togglePlay()
        assertTrue(vm.ready.playing)
        assertEquals(mapOf("speed" to "auto"), analytics.events.last().properties)
        vm.advance(1.0)
        assertEquals("Auto plays the whole trip in about 60 s", TestReplays.DURATION / 60, vm.time.doubleValue, 1e-9)
        vm.togglePlay()
        vm.advance(5.0)
        assertFalse(vm.ready.playing)
        assertEquals(TestReplays.DURATION / 60, vm.time.doubleValue, 1e-9)
    }

    @Test fun `speed changes the rate immediately`() {
        val vm = viewModel()
        vm.togglePlay()
        vm.setSpeed(PlaybackSpeed.X10)
        vm.advance(2.0)
        assertEquals(20.0, vm.time.doubleValue, 1e-9)
        vm.setSpeed(PlaybackSpeed.X1)
        vm.advance(1.0)
        assertEquals(21.0, vm.time.doubleValue, 1e-9)
        vm.setSpeed(PlaybackSpeed.X30)
        vm.togglePlay()
        vm.togglePlay()
        assertEquals(mapOf("speed" to "30x"), analytics.events.last().properties)
    }

    @Test fun `reaching the end pauses there, and Play at the end restarts`() {
        val vm = viewModel()
        vm.togglePlay()
        vm.advance(120.0)
        assertFalse(vm.ready.playing)
        assertEquals(TestReplays.DURATION, vm.time.doubleValue, 0.0)
        assertEquals(1, analytics.names.count { it == "replay_completed" })
        vm.togglePlay()
        assertEquals(0.0, vm.time.doubleValue, 0.0)
        assertTrue(vm.ready.playing)
    }

    @Test fun `releasing the scrubber resumes playing when it was playing`() {
        val vm = viewModel()
        vm.togglePlay()
        vm.scrubStart()
        assertFalse("the marker follows the finger, not the clock", vm.ready.playing)
        assertTrue(vm.ready.scrubbing)
        vm.scrubTo(300.0)
        vm.advance(1.0)
        assertEquals(300.0, vm.time.doubleValue, 0.0)
        vm.scrubEnd()
        assertTrue(vm.ready.playing)
        assertFalse(vm.ready.scrubbing)
        assertEquals("replay_scrubbed", analytics.names.last())
    }

    @Test fun `releasing the scrubber stays paused when it was paused, and clamps to the trip`() {
        val vm = viewModel()
        vm.scrubStart()
        vm.scrubTo(-50.0)
        assertEquals(0.0, vm.time.doubleValue, 0.0)
        vm.scrubTo(10_000.0)
        assertEquals(TestReplays.DURATION, vm.time.doubleValue, 0.0)
        vm.scrubEnd()
        assertFalse(vm.ready.playing)
        vm.seek(120.0)
        assertEquals(120.0, vm.time.doubleValue, 0.0)
        assertFalse(vm.ready.playing)
        assertEquals(2, analytics.names.count { it == "replay_scrubbed" })
    }

    @Test fun `the span label follows the marker through each overlay span`() {
        val vm = viewModel()
        assertEquals(SegmentStyle.ESTIMATED, vm.spanAt(30.0)?.style)
        assertNull("observed data has no label", vm.spanAt(100.0))
        assertEquals(SegmentStyle.NO_GPS, vm.spanAt(220.0)?.style)
        assertEquals(SegmentStyle.EXCLUDED, vm.spanAt(420.0)?.style)
        assertNull(vm.spanAt(450.0))
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

    @Test fun `a missing or broken replay shows the error, and Try again reloads`() {
        repository.result = { null }
        val vm = viewModel()
        assertEquals(ReplayUiState.Error, vm.state.value)
        repository.result = { error("corrupt trip file") }
        vm.retry()
        assertEquals(ReplayUiState.Error, vm.state.value)
        repository.result = { TestReplays.trip() }
        vm.retry()
        assertTrue(vm.state.value is ReplayUiState.Ready)
        assertEquals(3, repository.calls)
    }

    @Test fun `the Pro lock seam shows the lock and loads nothing`() {
        val vm = viewModel(lock = { true })
        assertEquals(ReplayUiState.Locked, vm.state.value)
        assertEquals(0, repository.calls)
        vm.togglePlay()
        assertEquals(ReplayUiState.Locked, vm.state.value)
    }

    @Test fun `the legend reports its opening once per opening`() {
        val vm = viewModel()
        vm.openLegend()
        vm.openLegend()
        assertTrue(vm.ready.legendOpen)
        vm.closeLegend()
        vm.openLegend()
        assertEquals(2, analytics.names.count { it == "quality_overlay_legend_opened" })
        assertTrue(analytics.events.all { it.properties.keys.all { key -> key in setOf("source", "speed") } })
    }
}
