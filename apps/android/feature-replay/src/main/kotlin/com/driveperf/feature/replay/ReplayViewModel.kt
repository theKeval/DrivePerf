package com.driveperf.feature.replay

import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.settings.UnitSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What S-RPL-01 shows. The playback position is not here: it is [ReplayViewModel.time], read in the draw phase. */
sealed interface ReplayUiState {
    data object Loading : ReplayUiState

    /** "Couldn't load this drive's detail" with Try again (S-RPL-01 Err). */
    data object Error : ReplayUiState

    /** Free, older than 30 days (S-RPL-01 Lock); see [ReplayProLock]. */
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

    /** Wall-clock seconds since the last frame, from the display-linked loop while playing (AC4). */
    fun advance(frameSeconds: Double)

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

            override fun advance(frameSeconds: Double) = Unit

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

/** Which trip to replay, and from where it was opened (`replay_opened {source}`). */
data class ReplayRequest(val tripId: String, val source: ReplaySource = ReplaySource.TRIP_DETAIL)

/**
 * State holder for S-RPL-01 Replay (F-RPL-01) with the data-quality overlay (F-RPL-08). The screen's frame loop calls
 * [advance] from `withFrameNanos`; everything else is a user action. Opens paused at the start (AC1).
 */
@Suppress("TooManyFunctions") // one function per user action on the screen
class ReplayViewModel(
    private val request: ReplayRequest,
    private val repository: ReplayRepository,
    private val analytics: Analytics,
    private val maps: ReplayMapConfig,
    private val proLock: ReplayProLock = ReplayProLock.NotBuilt,
    units: StateFlow<UnitSystem> = MutableStateFlow(UnitSystem.METRIC),
) : ViewModel(), ReplayControls {
    private val content = MutableStateFlow<ReplayUiState>(ReplayUiState.Loading)
    private val tilesFailed = MutableStateFlow(false)
    private val position = mutableDoubleStateOf(0.0)

    /** Playback position in trip seconds. Snapshot state, so the map redraws the marker without recomposing. */
    val time: DoubleState get() = position

    /** Play state before the current scrub, restored on release (AC3). */
    private var resumeAfterScrub = false

    val state: StateFlow<ReplayUiState> = combine(content, maps.online, tilesFailed, units) { c, online, failed, u ->
        if (c is ReplayUiState.Ready) c.copy(renderer = renderer(online, failed), units = u) else c
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReplayUiState.Loading)

    init {
        analytics.track(ReplayEvent.Opened(request.source))
        load()
    }

    override fun retry() = load()

    override fun togglePlay() {
        val ready = ready() ?: return
        if (ready.playing) {
            update { it.copy(playing = false) }
            return
        }
        if (position.doubleValue >= ready.replay.durationS) position.doubleValue = 0.0 // Play at the end restarts
        update { it.copy(playing = true) }
        analytics.track(ReplayEvent.Played(ready.speed))
    }

    /** Changes the rate immediately, playing or not. */
    override fun setSpeed(speed: PlaybackSpeed) = update { it.copy(speed = speed) }

    /** Moves playback on by [frameSeconds] of wall-clock time at the current rate. Reaching the end pauses there. */
    override fun advance(frameSeconds: Double) {
        val ready = ready() ?: return
        if (!ready.playing || ready.scrubbing) return
        val next = position.doubleValue + frameSeconds * ready.replay.playbackRate(ready.speed)
        if (next >= ready.replay.durationS) {
            position.doubleValue = ready.replay.durationS
            update { it.copy(playing = false) }
            analytics.track(ReplayEvent.Completed)
        } else {
            position.doubleValue = next
        }
    }

    override fun scrubStart() {
        val ready = ready() ?: return
        if (ready.scrubbing) return
        resumeAfterScrub = ready.playing
        update { it.copy(scrubbing = true, playing = false) }
    }

    /** Moves the marker to [tS] while the finger is down (AC3: same frame). */
    override fun scrubTo(tS: Double) {
        val ready = ready() ?: return
        position.doubleValue = tS.coerceIn(0.0, ready.replay.durationS)
    }

    /** Release: back to the play state from before the drag, unless the drag ended at the very end. */
    override fun scrubEnd() {
        val ready = ready() ?: return
        if (!ready.scrubbing) return
        val resume = resumeAfterScrub && position.doubleValue < ready.replay.durationS
        update { it.copy(scrubbing = false, playing = resume) }
        analytics.track(ReplayEvent.Scrubbed)
    }

    /** A tap or an accessibility step on the scrubber: a scrub with no drag. */
    fun seek(tS: Double) {
        scrubStart()
        scrubTo(tS)
        scrubEnd()
    }

    /** The overlay span under the marker, for its label (F-RPL-08 AC2). */
    fun spanAt(tS: Double): SegmentStyle? = ready()?.timeline?.spanAt(tS)

    override fun openLegend() {
        if (ready()?.legendOpen != false) return
        update { it.copy(legendOpen = true) }
        analytics.track(ReplayEvent.LegendOpened)
    }

    override fun closeLegend() = update { it.copy(legendOpen = false) }

    /** The map never finished loading its tiles: draw the plain canvas with the offline note (AC5). */
    override fun onMapTilesFailed() {
        tilesFailed.value = true
    }

    private fun load() {
        content.value = ReplayUiState.Loading
        viewModelScope.launch {
            content.value = try {
                when {
                    proLock.isLocked(request.tripId) -> ReplayUiState.Locked
                    else -> repository.replay(request.tripId)?.let { ReplayUiState.Ready(it, ReplayTimeline(it)) } ?: ReplayUiState.Error
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                // A broken trip file must not crash the app: show Err ("Your recording is safe") with Try again.
                ReplayUiState.Error
            }
            position.doubleValue = 0.0
        }
    }

    private fun renderer(online: Boolean, failed: Boolean): MapRenderer = when {
        !online || (maps.hasMapsKey && failed) -> MapRenderer.CANVAS_OFFLINE
        !maps.hasMapsKey -> MapRenderer.CANVAS
        else -> MapRenderer.GOOGLE
    }

    private fun ready(): ReplayUiState.Ready? = content.value as? ReplayUiState.Ready

    private fun update(change: (ReplayUiState.Ready) -> ReplayUiState.Ready) {
        content.update { if (it is ReplayUiState.Ready) change(it) else it }
    }
}
