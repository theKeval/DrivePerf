package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.AnalysisChoices
import com.driveperf.domain.analysis.AnalysisPreferences
import com.driveperf.domain.analysis.AnalysisRepository
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.EventType
import com.driveperf.presentation.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Where Analysis was opened from: the `source` of `analysis_opened`. */
public enum class AnalysisSource(public val key: String) {
    TRIP_DETAIL("trip_detail"),
    REPLAY("replay"),
}

/** Why there is no analysis to show. */
public enum class AnalysisUnavailable {
    /** The trip does not exist, is still recording, or was discarded. */
    NOT_FOUND,

    /** Reserved for the Pro gate on drives older than 30 days (S-SUB-03). Never set until entitlements exist. */
    LOCKED,
}

/**
 * S-ANL-01 Analysis for one trip (F-RPL-05, F-RPL-06). It changes on load and on intents, never per frame: while the
 * finger drags, the app reads [AnalysisTimeline.readout] for the cursor itself and sends [AnalysisIntent.MoveCursor]
 * when the drag ends, so the presenter knows where to zoom around.
 *
 * @property zoom 1× … 16× around the cursor; [AnalysisTimeline] holds the visible window.
 * @property timeline the lanes, bands and ticks of the visible window; null until loaded.
 * @property gg the G-G card and S-ANL-02, with [ggFilter] applied; [ggExpanded] while S-ANL-02 is open.
 */
public data class AnalysisState(
    val tripId: String,
    val loading: Boolean = true,
    val loadingText: String = AnalysisCopy.loading(null),
    val unavailable: AnalysisUnavailable? = null,
    val summaryOnly: Boolean = false,
    val axis: AnalysisAxis = AnalysisAxis.TIME,
    val zoom: Double = 1.0,
    val cursorS: Double = 0.0,
    val channels: Set<AnalysisChannel> = AnalysisChannel.DEFAULTS,
    val channelOptions: List<ChannelOption> = emptyList(),
    val timeline: AnalysisTimeline? = null,
    val map: AnalysisMap? = null,
    val stats: List<StatTile> = emptyList(),
    val gg: GgView? = null,
    val ggFilter: EventType? = null,
    val ggExpanded: Boolean = false,
) {
    /** "4×" for the zoom stepper. */
    public val zoomLabel: String get() = "${AnalysisCopy.fixed(zoom, if (zoom % 1.0 == 0.0) 0 else 1)}×"

    public val canZoomIn: Boolean get() = zoom < AnalysisPresenter.MAX_ZOOM

    public val canZoomOut: Boolean get() = zoom > 1.0
}

/** What the user does on S-ANL-01, S-ANL-02 and S-ANL-03. */
public sealed interface AnalysisIntent {
    /** The back button. [AnalysisEffect.Close] leaves the screen. */
    public data object Back : AnalysisIntent

    /** Time / Distance (AC3), remembered per user. */
    public data class SetAxis(val axis: AnalysisAxis) : AnalysisIntent

    public data object ZoomIn : AnalysisIntent

    public data object ZoomOut : AnalysisIntent

    /** Double-tap: back to the whole drive. */
    public data object ResetZoom : AnalysisIntent

    /** A pinch step: multiplies the zoom by [factor], around the cursor. */
    public data class Pinch(val factor: Double) : AnalysisIntent

    /** The cursor is at [tS]: a drag ended, the map or a tick was tapped. The window follows it when it leaves. */
    public data class MoveCursor(val tS: Double) : AnalysisIntent

    /** Turns a channel on or off (S-ANL-03); the last one stays on. */
    public data class ToggleChannel(val channel: AnalysisChannel) : AnalysisIntent

    /** The G-G card was tapped: S-ANL-02 opens. */
    public data object ExpandGg : AnalysisIntent

    public data object CollapseGg : AnalysisIntent

    /** All (null), braking, acceleration or cornering (F-RPL-06 AC2). */
    public data class FilterGg(val type: EventType?) : AnalysisIntent

    /** A "Peaks on this drive" row: S-ANL-02 closes and the cursor goes to that event's peak. */
    public data class ShowEvent(val eventId: String) : AnalysisIntent

    /** The Replay button. */
    public data object OpenReplay : AnalysisIntent
}

/** One-shot navigation and toasts. */
public sealed interface AnalysisEffect {
    public data object Close : AnalysisEffect

    /** A short message, e.g. "Keep at least one channel on". */
    public data class Message(val text: String) : AnalysisEffect

    /** Open S-RPL-01 for [tripId]. */
    public data class OpenReplay(val tripId: String) : AnalysisEffect
}

/**
 * The Analysis workspace (S-ANL-01/02/03, F-RPL-05, F-RPL-06, ADR-002 section 4). It loads the trip's channels off the
 * main thread, keeps the window, axis, zoom and channels, persists the user's choices, and sends the analytics.
 *
 * Analytics (keys and fixed values only): `analysis_opened {source}` once loaded, `analysis_axis {time|distance}`,
 * `analysis_channel_toggled {channel}`, `gg_expanded` and `gg_filter {type}`.
 *
 * @param compute where the channels are built: an hour of 50 Hz motion data is too much for the main thread.
 */
@Suppress("LongParameterList", "TooManyFunctions") // the screen's inputs; one handler per intent
public class AnalysisPresenter(
    private val tripId: String,
    private val source: AnalysisSource,
    private val analyses: AnalysisRepository,
    private val preferences: AnalysisPreferences?,
    private val units: Flow<UnitSystem>,
    private val analytics: Analytics?,
    scope: CoroutineScope,
    private val compute: CoroutineContext = Dispatchers.Default,
) : BasePresenter<AnalysisState, AnalysisIntent, AnalysisEffect>(AnalysisState(tripId), scope) {
    /** The wave-0 stub's constructor: a presenter with nothing to load. */
    public constructor(tripId: String, scope: CoroutineScope) :
        this(tripId, AnalysisSource.TRIP_DETAIL, AnalysisRepository { null }, null, flowOf(UnitSystem.METRIC), null, scope)

    /** The scope [onMain] made for this presenter alone; [close] cancels it too. */
    private var ownedScope: CoroutineScope? = null
    private var analysis: TripAnalysis? = null
    private var unitSystem: UnitSystem = UnitSystem.METRIC
    private val saving = Mutex()

    init {
        this.scope.launch { load() }
        this.scope.launch {
            units.collect { u ->
                unitSystem = u
                rebuild()
            }
        }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    override fun dispatch(intent: AnalysisIntent) {
        if (intent == AnalysisIntent.Back) {
            emit(AnalysisEffect.Close)
            return
        }
        if (analysis == null) return
        when (intent) {
            is AnalysisIntent.SetAxis -> setAxis(intent.axis)
            AnalysisIntent.ZoomIn, AnalysisIntent.ZoomOut, AnalysisIntent.ResetZoom, is AnalysisIntent.Pinch -> zoom(intent)
            is AnalysisIntent.MoveCursor -> moveCursor(intent.tS)
            is AnalysisIntent.ToggleChannel -> toggle(intent.channel)
            AnalysisIntent.ExpandGg -> expandGg()
            AnalysisIntent.CollapseGg -> update { it.copy(ggExpanded = false) }
            is AnalysisIntent.FilterGg -> filterGg(intent.type)
            is AnalysisIntent.ShowEvent -> showEvent(intent.eventId)
            AnalysisIntent.OpenReplay -> emit(AnalysisEffect.OpenReplay(tripId))
            AnalysisIntent.Back -> Unit
        }
    }

    private suspend fun load() {
        // The channels, their pyramids and the G-G grid are all built here, off the main thread, in one state: the
        // screen never sees "loaded" without its timeline.
        val units = unitSystem
        val ready = withContext(compute) {
            val choices = preferences?.read() ?: AnalysisChoices()
            analyses.analysis(tripId)?.let { a ->
                val loaded = state.value.copy(
                    loading = false,
                    summaryOnly = a.data.summaryOnly,
                    map = AnalysisMap.of(a),
                    axis = choices.axis,
                    channels = choices.channels,
                )
                a to views(a, loaded, null)
            }
        }
        if (ready == null) {
            update { it.copy(loading = false, unavailable = AnalysisUnavailable.NOT_FOUND) }
            return
        }
        analysis = ready.first
        update { ready.second }
        if (unitSystem != units) rebuild() // the units changed while the channels were being built
        track("analysis_opened", "source" to source.key)
    }

    /** Rebuilds what depends on the window, the channels or the units. */
    private fun rebuild(window: Double? = null) {
        val a = analysis ?: return
        update { views(a, it, window) }
    }

    private fun views(a: TripAnalysis, s: AnalysisState, window: Double?): AnalysisState {
        val scale = a.data.axis(s.axis)
        val width = scale.length / s.zoom
        val start = window ?: centred(scale.xOf(s.cursorS), width, scale.length)
        return s.copy(
            loadingText = AnalysisCopy.loading(a.data.durationS),
            timeline = AnalysisTimeline.build(a, s.map, s.axis, start, start + width, s.channels, unitSystem),
            channelOptions = options(a, s.channels),
            stats = AnalysisPanels.stats(a, unitSystem),
            gg = AnalysisPanels.gg(a, s.ggFilter),
        )
    }

    private fun centred(x: Double, width: Double, length: Double): Double =
        (x - width / 2).coerceIn(0.0, (length - width).coerceAtLeast(0.0))

    private fun setAxis(axis: AnalysisAxis) {
        if (axis == state.value.axis) return
        update { it.copy(axis = axis) }
        rebuild()
        save()
        track("analysis_axis", "axis" to axis.key)
    }

    private fun zoom(intent: AnalysisIntent) {
        val z = state.value.zoom
        zoomTo(
            when (intent) {
                AnalysisIntent.ZoomIn -> z * 2
                AnalysisIntent.ZoomOut -> z / 2
                is AnalysisIntent.Pinch -> z * intent.factor
                else -> 1.0
            },
        )
    }

    private fun zoomTo(zoom: Double) {
        val z = zoom.coerceIn(1.0, MAX_ZOOM)
        if (z == state.value.zoom) return
        update { it.copy(zoom = z) }
        rebuild()
    }

    private fun moveCursor(tS: Double) {
        val a = analysis ?: return
        val t = tS.coerceIn(0.0, a.data.durationS)
        val x = state.value.timeline?.xOf(t)
        update { it.copy(cursorS = t) }
        if (x == null || x !in 0.0..1.0) rebuild()
    }

    private fun toggle(channel: AnalysisChannel) {
        val on = state.value.channels
        val next = if (channel in on) on - channel else on + channel
        if (next.isEmpty()) {
            emit(AnalysisEffect.Message(AnalysisCopy.LAST_CHANNEL))
            return
        }
        update { it.copy(channels = next) }
        rebuild(state.value.timeline?.x0)
        save()
        track("analysis_channel_toggled", "channel" to channel.key)
    }

    private fun expandGg() {
        if (state.value.ggExpanded) return
        update { it.copy(ggExpanded = true) }
        track("gg_expanded")
    }

    private fun filterGg(type: EventType?) {
        val a = analysis ?: return
        if (type == state.value.ggFilter) return
        update { it.copy(ggFilter = type, gg = AnalysisPanels.gg(a, type)) }
        track("gg_filter", "type" to AnalysisCopy.filterKey(type))
    }

    private fun showEvent(eventId: String) {
        val marker = analysis?.markers?.firstOrNull { it.eventId == eventId } ?: return
        update { it.copy(ggExpanded = false) }
        moveCursor(marker.tPeakS)
    }

    private fun options(a: TripAnalysis, on: Set<AnalysisChannel>): List<ChannelOption> = AnalysisChannel.entries.map { c ->
        val enabled = c == AnalysisChannel.QUALITY || a.data.track(c) != null
        val title = AnalysisCopy.channelTitle(c)
        val desc = AnalysisCopy.channelDescription(c)
        val stateWord = if (!enabled) "Not recorded on this drive" else if (c in on) "On" else "Off"
        ChannelOption(c, title, desc, c in on, c.pro, enabled, "$title, ${AnalysisCopy.channelPattern(c)}. $desc. $stateWord.")
    }

    /** Persists the current axis and channels; saves run one at a time and each writes the latest choices. */
    private fun save() {
        val prefs = preferences ?: return
        scope.launch {
            saving.withLock { prefs.save(AnalysisChoices(state.value.channels, state.value.axis)) }
        }
    }

    private fun track(name: String, vararg properties: Pair<String, String>) {
        analytics?.track(AnalysisEvent(name, properties.toMap()))
    }

    public companion object {
        public const val MAX_ZOOM: Double = 16.0

        /**
         * A presenter on the main thread in a scope of its own, for Swift, which cannot make a `CoroutineScope`. The app
         * uses `CoreServices.analysisPresenter`. Call [close] on deinit; it cancels the scope.
         */
        public fun onMain(tripId: String): AnalysisPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return AnalysisPresenter(tripId, scope).also { it.ownedScope = scope }
        }

        /** [onMain] with everything to load, for `CoreServices.analysisPresenter` and the iOS fixture. */
        @Suppress("LongParameterList") // the constructor's inputs, minus the scope
        public fun onMain(
            tripId: String,
            source: AnalysisSource,
            analyses: AnalysisRepository,
            preferences: AnalysisPreferences?,
            units: Flow<UnitSystem>,
            analytics: Analytics?,
        ): AnalysisPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return AnalysisPresenter(tripId, source, analyses, preferences, units, analytics, scope).also { it.ownedScope = scope }
        }
    }
}

/** An Analysis analytics event (PRD F-RPL-05, F-RPL-06): fixed keys and values only. */
public data class AnalysisEvent(override val name: String, override val properties: Map<String, String>) : AnalyticsEvent
