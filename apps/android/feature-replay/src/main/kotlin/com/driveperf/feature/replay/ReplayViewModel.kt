package com.driveperf.feature.replay

import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.presentation.replay.ReplayEffect
import com.driveperf.presentation.replay.ReplayIntent
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplayState
import com.driveperf.presentation.replay.ReplayUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** What S-RPL-01 shows. The playback position is not here: it is [ReplayViewModel.time], read in the draw phase. */
sealed interface ReplayUiState {
    data object Loading : ReplayUiState

    /** "Couldn't load this drive's detail" with Try again (S-RPL-01 Err). */
    data object Error : ReplayUiState

    /** Free, older than 30 days (S-RPL-01 Lock): the shared presenter's `ReplayUnavailable.LOCKED`, not set yet. */
    data object Locked : ReplayUiState

    data class Ready(
        val replay: Replay,
        val timeline: ReplayTimeline,
        val playing: Boolean = false,
        val speed: PlaybackSpeed = PlaybackSpeed.AUTO,
        val scrubbing: Boolean = false,
        val renderer: MapRenderer = MapRenderer.CANVAS,
        val legendOpen: Boolean = false,
        val units: UnitSystem = UnitSystem.METRIC,
    ) : ReplayUiState
}

/** What S-RPL-01's controls do; the view model implements it. [None] does nothing (previews). */
interface ReplayControls {
    fun togglePlay()

    fun setSpeed(speed: PlaybackSpeed)

    /** One display frame while playing (AC4): reads the playhead from the shared presenter's clock. */
    fun onFrame()

    fun scrubStart()

    fun scrubTo(tS: Double)

    fun scrubEnd()

    fun openLegend()

    fun closeLegend()

    /** The map never finished loading its tiles. */
    fun onMapTilesFailed()

    fun retry()

    companion object {
        /** Controls that do nothing, for previews. */
        val None: ReplayControls = object : ReplayControls {
            override fun togglePlay() = Unit

            override fun setSpeed(speed: PlaybackSpeed) = Unit

            override fun onFrame() = Unit

            override fun scrubStart() = Unit

            override fun scrubTo(tS: Double) = Unit

            override fun scrubEnd() = Unit

            override fun openLegend() = Unit

            override fun closeLegend() = Unit

            override fun onMapTilesFailed() = Unit

            override fun retry() = Unit
        }
    }
}

/**
 * The Android side of S-RPL-01 (F-RPL-01, F-RPL-08): a thin wrapper around the shared [ReplayPresenter] (ADR-002
 * section 4), which owns playback, scrubbing, loading, the recording gate and analytics. This keeps only what is
 * Android's: the frame clock ([onFrame] from `withFrameNanos`), the playhead as Compose snapshot state, the legend sheet,
 * and which renderer draws the map (Maps key, network, tiles).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // one override per screen control (ReplayControls), plus the presenter plumbing
class ReplayViewModel(
    private val presenterFactory: (CoroutineScope) -> ReplayPresenter,
    private val maps: ReplayMapConfig,
    units: StateFlow<UnitSystem> = MutableStateFlow(UnitSystem.METRIC),
) : ViewModel(), ReplayControls {
    private val current = MutableStateFlow(presenterFactory(viewModelScope))
    private val presenter: ReplayPresenter get() = current.value
    private val legendOpen = MutableStateFlow(false)
    private val tilesFailed = MutableStateFlow(false)
    private val position = mutableDoubleStateOf(0.0)
    private var timeline: ReplayTimeline? = null

    /** Playback position in trip seconds. Snapshot state, so the map redraws the marker without recomposing. */
    val time: DoubleState get() = position

    /** One-shot navigation from the presenter: the trip is recording, so show the drive screen (AC6). */
    val effects: Flow<ReplayEffect> = current.flatMapLatest { it.effects }

    val state: StateFlow<ReplayUiState> = combine(
        current.flatMapLatest { it.state },
        legendOpen,
        combine(maps.online, tilesFailed, ::renderer),
        units,
    ) { shared, legend, renderer, u -> ui(shared, legend, renderer, u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReplayUiState.Loading)

    init {
        // A load (or any presenter-side change) moves the playhead too; intents also sync it at once, below.
        current.flatMapLatest { it.state }.onEach { sync() }.launchIn(viewModelScope)
    }

    override fun togglePlay() = send(if (presenter.state.value.playing) ReplayIntent.Pause else ReplayIntent.Play)

    override fun setSpeed(speed: PlaybackSpeed) = send(ReplayIntent.SetSpeed(speed))

    /**
     * Each frame: the playhead on the presenter's own clock. The presenter's Tick only detects the end, so it goes out
     * once the playhead reaches it, not every frame (no per-frame intent).
     */
    override fun onFrame() {
        val shared = presenter.state.value
        val now = presenter.nowNs()
        val t = shared.playheadAt(now)
        position.doubleValue = t
        val duration = shared.replay?.durationS ?: return
        if (shared.playing && t >= duration) send(ReplayIntent.Tick(now))
    }

    override fun scrubStart() = send(ReplayIntent.ScrubStart)

    override fun scrubTo(tS: Double) = send(ReplayIntent.ScrubTo(tS))

    override fun scrubEnd() = send(ReplayIntent.ScrubEnd)

    override fun openLegend() {
        if (legendOpen.value) return
        legendOpen.value = true
        send(ReplayIntent.OpenLegend)
    }

    override fun closeLegend() {
        legendOpen.value = false
    }

    override fun onMapTilesFailed() {
        tilesFailed.value = true
    }

    /** Try again after Err: a fresh presenter loads the trip again. */
    override fun retry() {
        presenter.close()
        timeline = null
        current.value = presenterFactory(viewModelScope)
    }

    private fun send(intent: ReplayIntent) {
        presenter.dispatch(intent)
        sync()
    }

    private fun sync() {
        position.doubleValue = presenter.state.value.playheadAt(presenter.nowNs())
    }

    private fun ui(shared: ReplayState, legend: Boolean, renderer: MapRenderer, units: UnitSystem): ReplayUiState {
        val replay = shared.replay
        return when {
            replay != null -> ReplayUiState.Ready(
                replay = replay,
                timeline = timelineFor(replay),
                playing = shared.playing,
                speed = shared.speed,
                scrubbing = shared.scrubbing,
                renderer = renderer,
                legendOpen = legend,
                units = units,
            )
            shared.unavailable == ReplayUnavailable.LOCKED -> ReplayUiState.Locked
            shared.unavailable == ReplayUnavailable.NOT_FOUND -> ReplayUiState.Error
            // Loading, or RECORDING: the screen leaves through ShowDriveScreen.
            else -> ReplayUiState.Loading
        }
    }

    private fun timelineFor(replay: Replay): ReplayTimeline =
        timeline?.takeIf { it.replay === replay } ?: ReplayTimeline(replay).also { timeline = it }

    private fun renderer(online: Boolean, failed: Boolean): MapRenderer = when {
        !online || (maps.hasMapsKey && failed) -> MapRenderer.CANVAS_OFFLINE
        !maps.hasMapsKey -> MapRenderer.CANVAS
        else -> MapRenderer.GOOGLE
    }
}
