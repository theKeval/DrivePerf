package com.driveperf.presentation.replay

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.replay.NoReplayDetails
import com.driveperf.domain.replay.PlaybackSpeed
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayDetails
import com.driveperf.domain.replay.ReplayDetailsRepository
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.presentation.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Where Replay was opened from: the `source` of `replay_opened`. */
public enum class ReplaySource(public val key: String) {
    TRIP_DETAIL("trip_detail"),
    STORY("story"),
}

/** Why there is no replay to show. */
public enum class ReplayUnavailable {
    /** The trip does not exist, is discarded, or has no position at all. */
    NOT_FOUND,

    /** The trip is being recorded: the app shows the drive screen instead (F-RPL-01 AC6, [ReplayEffect.ShowDriveScreen]). */
    RECORDING,

    /** Reserved for the Pro gate on drives older than 30 days (S-SUB-03). Never set yet. */
    LOCKED,
}

/**
 * The Replay screen (S-RPL-01). It changes on load and on user intents only, never per frame: each frame the app reads
 * `state.playheadAt(presenter.nowNs())`, then `replay.frameAt(t)` and `replay.spanAt(t)` itself.
 *
 * The play position is an anchor: at monotonic time [anchorNs] the playhead was at [anchorTS] trip seconds, and while
 * [playing] it advances at `replay.playbackRate(speed)`.
 *
 * @property resumeAfterScrub whether playback resumes when the current scrub ends (F-RPL-01 AC3).
 * @property overlay the pins, ticks, stats and elevation over the route (F-RPL-02, F-RPL-03); null until they load,
 *   and for a replay with no details (fixtures).
 * @property selection the pin the user tapped: it draws larger and the others dim (S-RPL-01).
 * @property pinSheet S-RPL-03 for a tapped stop or max-speed pin, or null.
 */
public data class ReplayState(
    val loading: Boolean = true,
    val replay: Replay? = null,
    val unavailable: ReplayUnavailable? = null,
    val playing: Boolean = false,
    val speed: PlaybackSpeed = PlaybackSpeed.AUTO,
    val scrubbing: Boolean = false,
    val anchorTS: Double = 0.0,
    val anchorNs: Long = 0L,
    val resumeAfterScrub: Boolean = false,
    val overlay: ReplayOverlay? = null,
    val selection: ReplaySelection? = null,
    val pinSheet: ReplayPinSheet? = null,
) {
    /**
     * The playhead in trip seconds at monotonic [nowNs], clamped to `[0, durationS]`. Pass `presenter.nowNs()`, so the
     * playhead and the anchors share one clock. Zero while nothing is loaded.
     */
    public fun playheadAt(nowNs: Long): Double {
        val r = replay ?: return 0.0
        val advanced = if (playing && !scrubbing) (nowNs - anchorNs) / NS_PER_S * r.playbackRate(speed) else 0.0
        return (anchorTS + advanced).coerceIn(0.0, r.durationS)
    }

    private companion object {
        const val NS_PER_S = 1e9
    }
}

/** What the user does on S-RPL-01. */
public sealed interface ReplayIntent {
    /** Plays from the playhead; at the end it restarts from the beginning. */
    public data object Play : ReplayIntent

    public data object Pause : ReplayIntent

    public data class SetSpeed(val speed: PlaybackSpeed) : ReplayIntent

    /** The finger is down on the scrubber: playback holds until [ScrubEnd]. */
    public data object ScrubStart : ReplayIntent

    /** Moves the playhead to [tS]. Outside a scrub gesture (accessibility adjust) it is a one-off seek. */
    public data class ScrubTo(val tS: Double) : ReplayIntent

    /** The finger is up: playback returns to what it was before [ScrubStart] (it stays paused at the very end). */
    public data object ScrubEnd : ReplayIntent

    /** A display frame at monotonic [nowNs] (`presenter.nowNs()`). Only detects the end: it pauses at the last second. */
    public data class Tick(val nowNs: Long) : ReplayIntent

    /** The quality legend was opened (F-RPL-08). */
    public data object OpenLegend : ReplayIntent

    /**
     * An event pin or timeline tick was tapped (F-RPL-03, F-RPL-04 AC5): playback pauses, the marker moves to the
     * event's peak, the pin is selected and S-RPL-02 opens ([ReplayEffect.OpenEvidence]).
     */
    public data class SelectEvent(val eventId: String) : ReplayIntent

    /**
     * The evidence sheet stepped to another event ("‹ Event 1 of 2 ›"): the marker and the selection follow, with no
     * new sheet and no analytics.
     */
    public data class ShowEvent(val eventId: String) : ReplayIntent

    /** A stop pin was tapped: S-RPL-03 for stop [number] (F-RPL-02 AC3). */
    public data class SelectStop(val number: Int) : ReplayIntent

    /** The max-speed pin was tapped: S-RPL-03 for the max speed (F-RPL-02 AC2). */
    public data object SelectMaxSpeed : ReplayIntent

    /** S-RPL-03 "Jump to this moment": the marker moves to the pin's time, paused, and the sheet closes. */
    public data object JumpToPin : ReplayIntent

    /** A sheet closed: the selection clears. */
    public data object ClearSelection : ReplayIntent

    /** The user's feedback on an event changed (S-RPL-02, S-SCO-03): the pins and stats are read again. */
    public data object ReloadDetails : ReplayIntent
}

/** One-shot navigation. */
public sealed interface ReplayEffect {
    /** The trip is still being recorded: show the drive screen S-DRV-05 instead (F-RPL-01 AC6). */
    public data object ShowDriveScreen : ReplayEffect

    /** Open S-RPL-02 for [eventId] over the replay. */
    public data class OpenEvidence(val tripId: String, val eventId: String) : ReplayEffect
}

/** The pin the user tapped (S-RPL-01). */
public sealed interface ReplaySelection {
    public data class Event(val eventId: String) : ReplaySelection

    public data class Stop(val number: Int) : ReplaySelection

    public data object MaxSpeed : ReplaySelection
}

/**
 * The shared Replay playback state machine (F-RPL-01, F-RPL-08, ADR-002 section 4). Both apps wrap it and draw from
 * its [state]; only map drawing and the frame loop are native.
 *
 * Analytics (keys and fixed values only): `replay_opened {source}` once the replay loads, `replay_played {speed}` on
 * each Play, `replay_scrubbed` once per scrub gesture or seek, `replay_completed` when playback reaches the end,
 * `quality_overlay_legend_opened`, `event_pin_tapped {type, confidence}` (F-RPL-03) and
 * `replay_pin_tapped {type: max_speed|stop}` (F-RPL-02).
 *
 * @param isRecording whether [tripId] is the trip being recorded right now.
 * @param monoNowNs the monotonic clock behind every anchor; apps read it through [nowNs].
 * @param details the pins and stats over the route (DRIVE-34, DRIVE-203); [NoReplayDetails] draws the route alone.
 * @param units the user's unit system, for the overlay's words.
 * @param format the app's time-of-day formatting.
 */
@Suppress("LongParameterList", "TooManyFunctions") // the screen's inputs, and one handler per intent
public class ReplayPresenter(
    private val tripId: String,
    private val source: ReplaySource,
    private val replays: ReplayRepository,
    private val isRecording: () -> Boolean,
    private val analytics: Analytics,
    private val monoNowNs: () -> Long,
    scope: CoroutineScope,
    private val details: ReplayDetailsRepository = NoReplayDetails,
    private val units: Flow<UnitSystem> = flowOf(UnitSystem.METRIC),
    private val format: ReplayTimeFormat = ReplayTimeFormat.None,
) : BasePresenter<ReplayState, ReplayIntent, ReplayEffect>(ReplayState(), scope) {
    /** The scope [onMain] made for this presenter alone; [close] cancels it too. */
    private var ownedScope: CoroutineScope? = null

    /** The latest details; the overlay is rebuilt from them whenever they or the units change. */
    private val loadedDetails = MutableStateFlow<ReplayDetails?>(null)
    private var currentUnits: UnitSystem = UnitSystem.METRIC

    init {
        this.scope.launch { load() }
        this.scope.launch {
            combine(loadedDetails.filterNotNull(), units) { d, u -> d to u }.collect { (d, u) ->
                currentUnits = u
                update { it.copy(overlay = ReplayCopy.overlay(d, u, format)) }
            }
        }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    /** Monotonic now on the presenter's clock: pass it to [ReplayState.playheadAt] and [ReplayIntent.Tick]. */
    public fun nowNs(): Long = monoNowNs()

    @Suppress("CyclomaticComplexMethod") // one branch per intent
    override fun dispatch(intent: ReplayIntent) {
        if (state.value.replay == null && intent != ReplayIntent.OpenLegend) return
        when (intent) {
            ReplayIntent.Play -> play()
            ReplayIntent.Pause -> rebase { it.copy(playing = false) }
            is ReplayIntent.SetSpeed -> rebase { it.copy(speed = intent.speed) }
            ReplayIntent.ScrubStart -> scrubStart()
            is ReplayIntent.ScrubTo -> scrubTo(intent.tS)
            ReplayIntent.ScrubEnd -> scrubEnd()
            is ReplayIntent.Tick -> tick(intent.nowNs)
            ReplayIntent.OpenLegend -> analytics.replayEvent("quality_overlay_legend_opened")
            is ReplayIntent.SelectEvent -> selectEvent(intent.eventId, open = true)
            is ReplayIntent.ShowEvent -> selectEvent(intent.eventId, open = false)
            is ReplayIntent.SelectStop -> selectStop(intent.number)
            ReplayIntent.SelectMaxSpeed -> selectMaxSpeed()
            ReplayIntent.JumpToPin -> jumpToPin()
            ReplayIntent.ClearSelection -> update { it.copy(selection = null, pinSheet = null) }
            ReplayIntent.ReloadDetails -> scope.launch { loadDetails() }
        }
    }

    private fun selectEvent(eventId: String, open: Boolean) {
        val d = loadedDetails.value
        val marker = d?.marker(eventId) ?: return
        pauseAt(marker.tPeakS) { it.copy(selection = ReplaySelection.Event(eventId), pinSheet = null) }
        if (open) openEvidence(d, marker)
    }

    private fun openEvidence(d: ReplayDetails, marker: com.driveperf.domain.replay.EventMarker) {
        val eventId = marker.eventId
        val confidence = d.events[eventId]?.confidence?.name?.lowercase() ?: "unknown"
        analytics.replayEvent("event_pin_tapped", "type" to marker.type.name.lowercase(), "confidence" to confidence)
        emit(ReplayEffect.OpenEvidence(tripId, eventId))
    }

    private fun selectStop(number: Int) {
        val d = loadedDetails.value ?: return
        val stop = d.stops.firstOrNull { it.number == number } ?: return
        val sheet = ReplayCopy.stopSheet(stop, d.stops.size, d.startedAtMs, format)
        pauseAt(null) { it.copy(selection = ReplaySelection.Stop(number), pinSheet = sheet) }
        analytics.replayEvent("replay_pin_tapped", "type" to "stop")
    }

    private fun selectMaxSpeed() {
        val d = loadedDetails.value ?: return
        val pin = d.maxSpeed ?: return
        val sheet = ReplayCopy.maxSpeedSheet(pin, currentUnits, d.startedAtMs, format)
        pauseAt(null) { it.copy(selection = ReplaySelection.MaxSpeed, pinSheet = sheet) }
        analytics.replayEvent("replay_pin_tapped", "type" to "max_speed")
    }

    private fun jumpToPin() {
        val sheet = state.value.pinSheet ?: return
        pauseAt(sheet.tS) { it.copy(selection = null, pinSheet = null) }
    }

    /** Pauses, moves the playhead to [tS] (or keeps it), then applies [change]. */
    private fun pauseAt(tS: Double?, change: (ReplayState) -> ReplayState) {
        val now = monoNowNs()
        update {
            val at = tS?.coerceIn(0.0, it.replay?.durationS ?: 0.0) ?: it.playheadAt(now)
            change(it.copy(playing = false, scrubbing = false, anchorTS = at, anchorNs = now))
        }
    }

    private suspend fun loadDetails() {
        val replay = state.value.replay ?: return
        loadedDetails.value = details.details(tripId, replay)
    }

    private suspend fun load() {
        if (isRecording()) {
            update { it.copy(loading = false, unavailable = ReplayUnavailable.RECORDING) }
            emit(ReplayEffect.ShowDriveScreen)
            return
        }
        val replay = replays.replay(tripId)
        update { it.copy(loading = false, replay = replay, unavailable = if (replay == null) ReplayUnavailable.NOT_FOUND else null) }
        if (replay != null) {
            analytics.replayEvent("replay_opened", "source" to source.key)
            loadDetails()
        }
    }

    private fun play() {
        val s = state.value
        if (s.playing) return
        val now = monoNowNs()
        val duration = s.replay?.durationS ?: 0.0
        val from = s.playheadAt(now).let { if (it >= duration) 0.0 else it }
        update { it.copy(playing = true, scrubbing = false, anchorTS = from, anchorNs = now) }
        analytics.replayEvent("replay_played", "speed" to s.speed.key)
    }

    /** Re-anchors at now, then applies [change], so the playhead never jumps. */
    private fun rebase(change: (ReplayState) -> ReplayState) {
        val now = monoNowNs()
        update { change(it.copy(anchorTS = it.playheadAt(now), anchorNs = now)) }
    }

    private fun scrubStart() {
        if (state.value.scrubbing) return
        rebase { it.copy(scrubbing = true, resumeAfterScrub = it.playing, playing = false) }
        analytics.replayEvent("replay_scrubbed")
    }

    private fun scrubTo(tS: Double) {
        val now = monoNowNs()
        val gesture = state.value.scrubbing
        update { it.copy(anchorTS = tS.coerceIn(0.0, it.replay?.durationS ?: 0.0), anchorNs = now) }
        if (!gesture) analytics.replayEvent("replay_scrubbed")
    }

    private fun scrubEnd() {
        if (!state.value.scrubbing) return
        val now = monoNowNs()
        update {
            val resume = it.resumeAfterScrub && it.anchorTS < (it.replay?.durationS ?: 0.0)
            it.copy(scrubbing = false, playing = resume, resumeAfterScrub = false, anchorNs = now)
        }
    }

    private fun tick(nowNs: Long) {
        val s = state.value
        val duration = s.replay?.durationS ?: return
        if (!s.playing || s.scrubbing || s.playheadAt(nowNs) < duration) return
        update { it.copy(playing = false, anchorTS = duration, anchorNs = nowNs) }
        analytics.replayEvent("replay_completed")
    }

    public companion object {
        /**
         * A presenter on the main thread in a scope of its own, for Swift, which cannot make a `CoroutineScope`. For
         * the iOS UI-test fixture and previews: pass a `StaticReplayRepository` holding a synthetic `Replay`. The app
         * itself uses `CoreServices.replayPresenter`. Call [close] on deinit; it cancels the scope.
         */
        @Suppress("LongParameterList") // the constructor's inputs, minus the scope
        public fun onMain(
            tripId: String,
            source: ReplaySource,
            replays: ReplayRepository,
            isRecording: () -> Boolean,
            analytics: Analytics,
            monoNowNs: () -> Long,
        ): ReplayPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return ReplayPresenter(tripId, source, replays, isRecording, analytics, monoNowNs, scope).also { it.ownedScope = scope }
        }

        /**
         * [onMain] with the pins and stats over the route (DRIVE-34, DRIVE-203). For Swift fixtures pass a
         * `StaticReplayDetails`; the app uses `CoreServices.replayPresenter(tripId, source, region, format)`.
         */
        @Suppress("LongParameterList") // the constructor's inputs, minus the scope
        public fun onMain(
            tripId: String,
            source: ReplaySource,
            replays: ReplayRepository,
            isRecording: () -> Boolean,
            analytics: Analytics,
            monoNowNs: () -> Long,
            details: ReplayDetailsRepository,
            units: Flow<UnitSystem>,
            format: ReplayTimeFormat,
        ): ReplayPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return ReplayPresenter(tripId, source, replays, isRecording, analytics, monoNowNs, scope, details, units, format)
                .also { it.ownedScope = scope }
        }
    }
}

private fun Analytics.replayEvent(name: String, vararg properties: Pair<String, String>) {
    track(ReplayEvent(name, properties.toMap()))
}

/** The `speed` value of `replay_played`. */
private val PlaybackSpeed.key: String
    get() = when (this) {
        PlaybackSpeed.AUTO -> "auto"
        PlaybackSpeed.X1 -> "1x"
        PlaybackSpeed.X10 -> "10x"
        PlaybackSpeed.X30 -> "30x"
    }

/** A Replay analytics event (PRD F-RPL-01, F-RPL-08): fixed keys and values only. */
public data class ReplayEvent(override val name: String, override val properties: Map<String, String>) : AnalyticsEvent
