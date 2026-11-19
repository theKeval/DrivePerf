package com.driveperf.feature.replay

import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.presentation.evidence.EvidenceEffect
import com.driveperf.presentation.evidence.EvidenceIntent
import com.driveperf.presentation.evidence.EvidencePresenter
import com.driveperf.presentation.evidence.EvidenceState
import com.driveperf.presentation.evidence.TipDetailEffect
import com.driveperf.presentation.evidence.TipDetailIntent
import com.driveperf.presentation.evidence.TipDetailPresenter
import com.driveperf.presentation.evidence.TipDetailState
import com.driveperf.presentation.replay.ReplayEffect
import com.driveperf.presentation.replay.ReplayIntent
import com.driveperf.presentation.replay.ReplayOverlay
import com.driveperf.presentation.replay.ReplayPinSheet
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySelection
import com.driveperf.presentation.replay.ReplayState
import com.driveperf.presentation.replay.ReplayUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
        /** Pins, ticks, stats and elevation (DRIVE-34, DRIVE-203); null until they load. */
        val overlay: ReplayOverlay? = null,
        val selection: ReplaySelection? = null,
        /** S-RPL-03 for a tapped stop or the max speed. */
        val pinSheet: ReplayPinSheet? = null,
        /** S-RPL-02 (with S-SCO-03) while it is open. */
        val evidence: EvidenceState? = null,
        /** S-SCO-04 while it is open. */
        val tip: TipDetailState? = null,
    ) : ReplayUiState
}

/** Opens the shared sheets over Replay: S-RPL-02 for an event, S-SCO-04 for a tip. Null in previews and tests. */
class ReplaySheets(
    val evidence: (eventId: String, scope: CoroutineScope) -> EvidencePresenter,
    val tip: (tipId: String, eventId: String, scope: CoroutineScope) -> TipDetailPresenter,
)

/** What S-RPL-01's controls do; the view model implements it. [None] does nothing (previews). */
@Suppress("TooManyFunctions") // one function per control on S-RPL-01, S-RPL-02, S-RPL-03 and S-SCO-04
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

    /** An event pin or tick: pause there and open S-RPL-02 (F-RPL-03, F-RPL-04 AC5). */
    fun selectEvent(eventId: String) = Unit

    /** A stop pin: S-RPL-03 (F-RPL-02 AC3). */
    fun selectStop(number: Int) = Unit

    /** The max-speed pin: S-RPL-03 (F-RPL-02 AC2). */
    fun selectMaxSpeed() = Unit

    /** S-RPL-03 "Jump to this moment". */
    fun jumpToPin() = Unit

    /** S-RPL-03 closed. */
    fun clearSelection() = Unit

    /** An action on S-RPL-02 or S-SCO-03. */
    fun evidence(intent: EvidenceIntent) = Unit

    /** S-RPL-02 closed. */
    fun closeEvidence() = Unit

    /** An action on S-SCO-04. */
    fun tip(intent: TipDetailIntent) = Unit

    /** S-SCO-04 closed. */
    fun closeTip() = Unit

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
    private val sheets: ReplaySheets? = null,
) : ViewModel(), ReplayControls {
    private val current = MutableStateFlow(presenterFactory(viewModelScope))
    private val presenter: ReplayPresenter get() = current.value
    private val evidencePresenter = MutableStateFlow<EvidencePresenter?>(null)
    private val tipPresenter = MutableStateFlow<TipDetailPresenter?>(null)
    private val toasts = Channel<String>(Channel.BUFFERED)
    private val legendOpen = MutableStateFlow(false)
    private val tilesFailed = MutableStateFlow(false)
    private val position = mutableDoubleStateOf(0.0)
    private var timeline: ReplayTimeline? = null

    /** Playback position in trip seconds. Snapshot state, so the map redraws the marker without recomposing. */
    val time: DoubleState get() = position

    private val navigation = Channel<ReplayEffect>(Channel.BUFFERED)

    /**
     * One-shot navigation from the presenter: the trip is recording, so show the drive screen (AC6). The view model is
     * the presenter's only effect collector (each effect reaches one collector) and handles the sheets itself.
     */
    val effects: Flow<ReplayEffect> = navigation.receiveAsFlow()

    /** Short confirmations from the sheets ("Counted again · Smoothness recomputed"), for a snackbar. */
    val messages: Flow<String> = toasts.receiveAsFlow()

    private val sheetStates: Flow<Pair<EvidenceState?, TipDetailState?>> = combine(
        evidencePresenter.flatMapLatest { it?.state ?: flowOf(null) },
        tipPresenter.flatMapLatest { it?.state ?: flowOf(null) },
    ) { e, t -> e to t }

    val state: StateFlow<ReplayUiState> = combine(
        current.flatMapLatest { it.state },
        legendOpen,
        combine(maps.online, tilesFailed, ::renderer),
        units,
        sheetStates,
    ) { shared, legend, renderer, u, sheets -> ui(shared, legend, renderer, u, sheets) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReplayUiState.Loading)

    init {
        // A load (or any presenter-side change) moves the playhead too; intents also sync it at once, below.
        current.flatMapLatest { it.state }.onEach { sync() }.launchIn(viewModelScope)
        current.flatMapLatest { it.effects }.onEach { effect ->
            if (effect is ReplayEffect.OpenEvidence) openEvidence(effect.eventId) else navigation.trySend(effect)
        }.launchIn(viewModelScope)
        evidencePresenter.flatMapLatest { it?.effects ?: emptyFlow() }.onEach(::onEvidenceEffect).launchIn(viewModelScope)
        tipPresenter.flatMapLatest { it?.effects ?: emptyFlow() }.onEach(::onTipEffect).launchIn(viewModelScope)
    }

    override fun selectEvent(eventId: String) = send(ReplayIntent.SelectEvent(eventId))

    override fun selectStop(number: Int) = send(ReplayIntent.SelectStop(number))

    override fun selectMaxSpeed() = send(ReplayIntent.SelectMaxSpeed)

    override fun jumpToPin() = send(ReplayIntent.JumpToPin)

    override fun clearSelection() = send(ReplayIntent.ClearSelection)

    override fun evidence(intent: EvidenceIntent) {
        evidencePresenter.value?.dispatch(intent)
    }

    override fun closeEvidence() {
        evidencePresenter.value?.close()
        evidencePresenter.value = null
        send(ReplayIntent.ClearSelection)
    }

    override fun tip(intent: TipDetailIntent) {
        tipPresenter.value?.dispatch(intent)
    }

    override fun closeTip() {
        tipPresenter.value?.close()
        tipPresenter.value = null
    }

    /** S-RPL-02 over the paused replay; a second tap replaces the open sheet. */
    private fun openEvidence(eventId: String) {
        val open = sheets?.evidence ?: return
        evidencePresenter.value?.close()
        evidencePresenter.value = open(eventId, viewModelScope)
    }

    private fun onEvidenceEffect(effect: EvidenceEffect) {
        when (effect) {
            is EvidenceEffect.ShowEvent -> send(ReplayIntent.ShowEvent(effect.eventId))
            EvidenceEffect.FeedbackChanged -> send(ReplayIntent.ReloadDetails)
            is EvidenceEffect.OpenTip -> sheets?.tip?.let { open ->
                tipPresenter.value?.close()
                tipPresenter.value = open(effect.tipId, effect.eventId, viewModelScope)
            }
            is EvidenceEffect.Toast -> toasts.trySend(effect.text)
            EvidenceEffect.Close -> closeEvidence()
        }
    }

    private fun onTipEffect(effect: TipDetailEffect) {
        when (effect) {
            // "See the moment" from the tip: back to the evidence sheet underneath, on that event.
            is TipDetailEffect.OpenEvidence -> {
                closeTip()
                if (evidencePresenter.value?.state?.value?.eventId != effect.eventId) send(ReplayIntent.SelectEvent(effect.eventId))
            }
            TipDetailEffect.Close -> closeTip()
        }
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

    private fun ui(
        shared: ReplayState,
        legend: Boolean,
        renderer: MapRenderer,
        units: UnitSystem,
        sheets: Pair<EvidenceState?, TipDetailState?>,
    ): ReplayUiState {
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
                overlay = shared.overlay,
                selection = shared.selection,
                pinSheet = shared.pinSheet,
                evidence = sheets.first,
                tip = sheets.second,
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
