package com.driveperf.presentation.drives

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.score.ScoreLines
import com.driveperf.domain.score.ScoreReader
import com.driveperf.domain.score.TripScoreDetail
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.foundation.Clock
import com.driveperf.presentation.BasePresenter
import com.driveperf.presentation.score.ScoreExplainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Drives tab, All drives, trip detail, the Smoothness breakdown, How scores work and the data-quality sheet
 * (S-HOME-01, S-HIS-01, S-TRP-01, S-SCO-01, S-SCO-02, S-DQ-01; ADR-002 section 4). It owns the trip and Not drives
 * lists with their scores, the filter, which trip is open, answers and corrections (F-CAP-04), the score surfaces
 * (DRIVE-77, DRIVE-82, DRIVE-87, DRIVE-55) and the hand-offs to Replay and Analysis. Both apps draw from [state]; only
 * dates and times are formatted natively ([DrivesFormatter]).
 *
 * The open trip follows its trip: it updates when the trip, its score or the units change, and closes itself when the
 * trip is gone.
 *
 * @param trips finished and interrupted trips, newest first (`TripRepository.observeTrips`).
 * @param corrections answers and corrections; null where they are not wired, and then nothing can be corrected.
 * @param units the user's unit system, from the shared settings.
 * @param clock its wall time decides "This week" and "Last week".
 * @param scores the trips' scores; null where they are not wired (previews), and then no score shows.
 * @param analytics `trip_detail_opened`, `score_viewed`, `scores_explainer_viewed`, `contributor_tapped` and
 *   `tip_evidence_opened`.
 * @param maps the open trip's map hero (DRIVE-55); null where it is not wired, and then the hero hides.
 */
@Suppress("LongParameterList", "TooManyFunctions") // the screens' inputs, injected so the state machine stays testable
public class DrivesPresenter(
    trips: Flow<List<Trip>>,
    private val corrections: TripCorrections?,
    units: Flow<UnitSystem>,
    private val format: DrivesFormatter,
    private val clock: Clock,
    scope: CoroutineScope,
    private val scores: ScoreReader? = null,
    private val analytics: Analytics? = null,
    private val maps: TripMapSource? = null,
) : BasePresenter<DrivesState, DrivesIntent, DrivesEffect>(DrivesState(canCorrect = corrections != null), scope) {
    /** Which screen the open trip's score is viewed on (`score_viewed {surface}`). */
    private enum class Surface(val key: String) { TRIP_DETAIL("trip_detail"), BREAKDOWN("breakdown") }

    /** What the state is built from. [trips] is null until the first list arrives. */
    private data class Inputs(
        val trips: List<Trip>? = null,
        val notDrives: List<Trip> = emptyList(),
        val units: UnitSystem = UnitSystem.METRIC,
        val detailId: String? = null,
        val qualityId: String? = null,
        /** The user's choice; null until they choose. */
        val filter: DrivesFilter? = null,
        val lines: ScoreLines = ScoreLines.EMPTY,
        /** The open trip's score; it may still be another trip's for a moment after [detailId] changes. */
        val detailScore: TripScoreDetail? = null,
        /** `trip_detail_opened` waits for the trip to be listed. */
        val pendingOpened: Boolean = false,
        /** `score_viewed` waits for the open trip's score. */
        val pendingView: Surface? = null,
        /** The open trip's map hero, by trip ID; null until loaded. */
        val detailMap: Pair<String, TripMapUi?>? = null,
    )

    private val inputs = MutableStateFlow(Inputs())

    /** The scope [onMain] made for this presenter alone; [close] cancels it too. */
    private var ownedScope: CoroutineScope? = null

    private val explainer = ScoreExplainer.content(format::longDate)

    init {
        update { it.copy(explainer = explainer) }
        this.scope.launch { trips.collect { list -> change { it.copy(trips = list) } } }
        corrections?.let { port ->
            this.scope.launch { port.observeNotDrives().collect { list -> change { it.copy(notDrives = list) } } }
        }
        this.scope.launch { units.collect { system -> change { it.copy(units = system) } } }
        scores?.let(::observeScores)
        maps?.let(::observeMap)
    }

    /**
     * Loads the open trip's map hero, and again when its feedback changes: an event reported "Not accurate?" leaves
     * the pins (DRIVE-55, DRIVE-39).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMap(source: TripMapSource) {
        scope.launch {
            inputs.map { i -> i.detailId?.let { id -> id to i.detailScore?.takeIf { it.tripId == id }?.feedback.orEmpty() } }
                .distinctUntilChanged()
                .mapLatest { key -> key?.let { (id, _) -> id to source.map(id) } }
                .collect { loaded -> change { it.copy(detailMap = loaded) } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeScores(reader: ScoreReader) {
        scope.launch { reader.observeLines().collect { lines -> change { it.copy(lines = lines) } } }
        scope.launch {
            inputs.map { it.detailId }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else reader.observeDetail(id) }
                .collect { detail -> change { it.copy(detailScore = detail) } }
        }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    @Suppress("CyclomaticComplexMethod") // one branch per intent; the score and cross-link ones are grouped
    override fun dispatch(intent: DrivesIntent) {
        when (intent) {
            is DrivesIntent.Open -> change { it.copy(detailId = intent.tripId, pendingOpened = true, pendingView = Surface.TRIP_DETAIL) }
            is DrivesIntent.OpenTrip, is DrivesIntent.OpenBreakdown, is DrivesIntent.ShowBreakdown,
            is DrivesIntent.OpenHowScoresWork, is DrivesIntent.TapContributor,
            -> scoreIntent(intent)
            DrivesIntent.CloseDetail -> change { it.copy(detailId = null, qualityId = null) }
            is DrivesIntent.OpenQuality -> change { it.copy(qualityId = intent.tripId) }
            DrivesIntent.CloseQuality -> change { it.copy(qualityId = null) }
            is DrivesIntent.Filter -> change { it.copy(filter = intent.filter) }
            is DrivesIntent.OpenReplay -> openReplay(intent.tripId)
            is DrivesIntent.OpenAnalysis -> openAnalysis(intent.tripId)
            is DrivesIntent.Answer -> corrections?.let { port ->
                scope.launch { port.answerSuspect(intent.tripId, if (intent.yes) SuspectAnswer.YES else SuspectAnswer.NO) }
            }
            is DrivesIntent.Correct -> corrections?.let { port -> scope.launch { port.correct(intent.tripId, intent.to) } }
            is DrivesIntent.OpenEvidence, is DrivesIntent.OpenTip, is DrivesIntent.OpenTipEvidence -> crossLink(intent)
        }
    }

    /** The pushes to the score screens and their analytics (DRIVE-55, DRIVE-87, DRIVE-113). */
    private fun scoreIntent(intent: DrivesIntent) {
        when (intent) {
            is DrivesIntent.OpenTrip -> emit(DrivesEffect.OpenTrip(intent.tripId))
            is DrivesIntent.OpenBreakdown -> emit(DrivesEffect.OpenBreakdown(intent.tripId))
            is DrivesIntent.ShowBreakdown -> change { it.copy(detailId = intent.tripId, pendingView = Surface.BREAKDOWN) }
            is DrivesIntent.OpenHowScoresWork -> {
                track("scores_explainer_viewed", "source" to intent.source.key)
                emit(DrivesEffect.OpenHowScoresWork)
            }
            is DrivesIntent.TapContributor -> track("contributor_tapped", "contributor" to intent.contributor.name.lowercase())
            else -> Unit
        }
    }

    /**
     * The links from trip detail and the breakdown to the evidence sheet and the tip (DRIVE-87 AC2, DRIVE-55, DRIVE-102).
     * An event reported "Not accurate?" is left out everywhere, so it has no evidence to open.
     */
    private fun crossLink(intent: DrivesIntent) {
        val detail = state.value.detail
        when (intent) {
            is DrivesIntent.OpenEvidence -> {
                val row = detail?.takeIf { it.id == intent.tripId }?.events?.rows?.firstOrNull { it.id == intent.eventId }
                if (row?.excluded != true) emit(DrivesEffect.OpenEvidence(intent.tripId, intent.eventId))
            }
            is DrivesIntent.OpenTip -> emit(DrivesEffect.OpenTip(intent.tripId))
            is DrivesIntent.OpenTipEvidence -> {
                val tip = detail?.takeIf { it.id == intent.tripId }?.tip ?: return
                val eventId = tip.eventId ?: return
                track("tip_evidence_opened", "tip_id" to tip.tipId)
                emit(DrivesEffect.OpenEvidence(intent.tripId, eventId))
            }
            else -> Unit
        }
    }

    /** Replay keeps the trip detail under it. */
    private fun openReplay(tripId: String) {
        val i = inputs.value
        val trip = (i.trips.orEmpty() + i.notDrives).firstOrNull { it.id == tripId } ?: return
        change { it.copy(qualityId = null) }
        emit(DrivesEffect.OpenReplay(tripId, rows(i).title(trip), "Map of your drive, ${distanceText(trip.distance, i.units)}."))
    }

    /** Analysis opens over the trip detail, which stays under it. */
    private fun openAnalysis(tripId: String) {
        val i = inputs.value
        val trip = (i.trips.orEmpty() + i.notDrives).firstOrNull { it.id == tripId } ?: return
        emit(DrivesEffect.OpenAnalysis(tripId, rows(i).title(trip)))
    }

    private fun rows(i: Inputs) = TripRows(i.units, format, clock.wallMs(), i.lines)

    /** Applies [change] to the inputs, records what became viewable, then rebuilds the state from the newest inputs. */
    private fun change(change: (Inputs) -> Inputs) {
        inputs.update(change)
        trackViews()
        update { render(inputs.value) }
    }

    /** `trip_detail_opened` once the opened trip is listed; `score_viewed` once its score has been read and is shown. */
    private fun trackViews() {
        val i = inputs.value
        val trip = i.detailId?.let { id -> (i.trips.orEmpty() + i.notDrives).firstOrNull { it.id == id } }
        if (i.pendingOpened && trip != null) {
            inputs.update { it.copy(pendingOpened = false) }
            track("trip_detail_opened", "source" to "drives", "age_bucket" to ageBucket(clock.wallMs() - trip.startedAt))
        }
        val surface = i.pendingView
        val detail = i.detailScore?.takeIf { trip != null && it.tripId == trip.id }
        if (surface != null && trip != null && detail != null) {
            inputs.update { it.copy(pendingView = null) }
            if (trip.scoresShown && detail.score?.smoothness != null) track("score_viewed", "surface" to surface.key)
        }
    }

    private fun track(name: String, vararg properties: Pair<String, String>) {
        analytics?.track(DrivesEvent(name, properties.toMap()))
    }

    private fun render(i: Inputs): DrivesState {
        val rows = rows(i)
        val trips = i.trips.orEmpty().sortedByDescending { it.startedAt }
        val notDrives = i.notDrives.sortedByDescending { it.startedAt }
        val tripRows = trips.map(rows::row)
        val byId = (trips + notDrives).associateBy { it.id }
        return DrivesState(
            loaded = i.trips != null,
            trips = tripRows,
            notDrives = notDrives.map(rows::row),
            sections = rows.sections(trips, tripRows),
            // With no drives left, All drives opens on the trips marked as not a drive.
            filter = i.filter ?: if (trips.isEmpty() && notDrives.isNotEmpty()) DrivesFilter.NOT_DRIVES else DrivesFilter.DRIVES,
            canCorrect = corrections != null,
            detail = i.detailId?.let(byId::get)?.let { detail(it, i, rows, trips, byId) },
            quality = i.qualityId?.let(byId::get)?.quality?.let(QualitySheet(i.units)::sheet),
            countText = drivesCount(trips.size),
            sinceText = trips.lastOrNull()?.let(rows::since),
            explainer = explainer,
        )
    }

    private fun detail(trip: Trip, i: Inputs, rows: TripRows, trips: List<Trip>, byId: Map<String, Trip>): TripDetailUi {
        val scoreDetail = i.detailScore?.takeIf { it.tripId == trip.id }
        val base = TripSheet(i.units, format).detail(trip, rows.title(trip), rows.subtitle(trip), scoreDetail?.car ?: rows.line(trip)?.car)
            .copy(map = map(trip, i))
        if (scoreDetail == null || !trip.scoresShown) return base
        val score = TripScore(i.units, format) { id -> byId[id]?.startedAt }
        // The user's first scored drive ever reads "Your baseline" rather than "Calibrating · 1 of 3" (§5.7).
        val firstScored = trips.lastOrNull { it.scoresShown && i.lines.lines[it.id]?.smoothness != null }
        return base.copy(
            score = score.score(trip, scoreDetail, firstScoredEver = firstScored?.id == trip.id),
            breakdown = TripBreakdown.build(trip, scoreDetail),
            events = score.events(trip, scoreDetail),
            cleanest = score.cleanest(trip, scoreDetail),
            tip = score.tip(scoreDetail),
        )
    }

    /** The open trip's map hero with its label; its event pins hide with the scores (F-RPL-07 AC4). */
    private fun map(trip: Trip, i: Inputs): TripMapUi? {
        val map = i.detailMap?.takeIf { it.first == trip.id }?.second ?: return null
        val pins = if (trip.scoresShown) map.pins else emptyList()
        return map.copy(
            pins = pins,
            estimatedStart = (trip.estimatedDistanceM ?: 0.0) > 0,
            accessibilityLabel = tripMapLabel(distanceText(trip.distance, i.units), pins.size, map.stops.size),
        )
    }

    public companion object {
        /**
         * A presenter on the main thread in a scope of its own, for Swift, which cannot make a `CoroutineScope`: the
         * iOS UI-test fixture and previews pass fixed [trips]. The app itself uses `CoreServices.drivesPresenter`.
         * Call [close] on deinit; it cancels the scope.
         */
        public fun onMain(trips: List<Trip>, units: UnitSystem, format: DrivesFormatter, clock: Clock): DrivesPresenter =
            onMain(flowOf(trips), null, flowOf(units), format, clock)

        /** [DrivesPresenter] on the main thread in a scope of its own (`CoreServices.drivesPresenter`). Call [close] when done. */
        public fun onMain(
            trips: Flow<List<Trip>>,
            corrections: TripCorrections?,
            units: Flow<UnitSystem>,
            format: DrivesFormatter,
            clock: Clock,
        ): DrivesPresenter = onMain(trips, corrections, units, format, clock, null, null)

        /** [onMain] with the trips' scores and analytics (`CoreServices.drivesPresenter`, and fixtures with scores). */
        @Suppress("LongParameterList") // the screens' inputs
        public fun onMain(
            trips: Flow<List<Trip>>,
            corrections: TripCorrections?,
            units: Flow<UnitSystem>,
            format: DrivesFormatter,
            clock: Clock,
            scores: ScoreReader?,
            analytics: Analytics?,
        ): DrivesPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return DrivesPresenter(trips, corrections, units, format, clock, scope, scores, analytics).also { it.ownedScope = scope }
        }

        /** [onMain] with the open trip's map hero too (`CoreServices.drivesPresenter`, DRIVE-55). */
        @Suppress("LongParameterList") // the screens' inputs
        public fun onMain(
            trips: Flow<List<Trip>>,
            corrections: TripCorrections?,
            units: Flow<UnitSystem>,
            format: DrivesFormatter,
            clock: Clock,
            scores: ScoreReader?,
            analytics: Analytics?,
            maps: TripMapSource?,
        ): DrivesPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return DrivesPresenter(trips, corrections, units, format, clock, scope, scores, analytics, maps)
                .also { it.ownedScope = scope }
        }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1_000L
private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30

/** `trip_age_bucket` (analytics-crash-privacy.md §2): `lt_1d`, `1_7d`, `8_30d`, `gt_30d`. */
internal fun ageBucket(ageMs: Long): String {
    val days = ageMs / DAY_MS
    return when {
        days < 1 -> "lt_1d"
        days <= WEEK_DAYS -> "1_7d"
        days <= MONTH_DAYS -> "8_30d"
        else -> "gt_30d"
    }
}

/** A Drives analytics event (F-RPL-07, F-SCO-01, F-SCO-03, F-SCO-08): fixed keys and bucketed values only. */
public data class DrivesEvent(override val name: String, override val properties: Map<String, String>) : AnalyticsEvent
