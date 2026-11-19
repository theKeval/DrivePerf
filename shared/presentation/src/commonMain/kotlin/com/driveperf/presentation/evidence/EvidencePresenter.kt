package com.driveperf.presentation.evidence

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.feedback.ReportEventInaccurate
import com.driveperf.domain.replay.EventEvidence
import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.EventMarkers
import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DisplayUnit
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Metric
import com.driveperf.domain.trip.Provenance
import com.driveperf.domain.trip.Trip
import com.driveperf.foundation.Outcome
import com.driveperf.presentation.BasePresenter
import com.driveperf.presentation.replay.ReplayCopy
import com.driveperf.presentation.replay.ReplayTimeFormat
import com.driveperf.presentation.score.TipCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One fact of S-RPL-02's grid, with the provenance glyph its label carries (F-DQ-05). */
public data class EvidenceFact(
    val label: String,
    val value: String,
    val unit: String,
    val provenance: Provenance,
    /** Struck through: the event was marked "Needed", so its peak no longer counts. */
    val struck: Boolean,
    /** What VoiceOver and TalkBack read: label, value, units spelled out and provenance. */
    val spoken: String,
)

/** A banner over the evidence (S-RPL-02 layout). */
public enum class EvidenceBannerKind { LOW_CONFIDENCE, NEEDED, TRACE_REMOVED }

public data class EvidenceBanner(val kind: EvidenceBannerKind, val text: String, val action: String?)

/**
 * The related coaching tip on S-RPL-02 (F-RPL-04 AC3, F-SCO-06): this drive's own tip when it is about this event,
 * otherwise the library tip for the event's type ("Next time"). Only shown once its words exist ([TipCopy.text]).
 */
public data class EvidenceTip(
    val tipId: String,
    val kicker: String,
    val title: String,
    val text: String,
    val thisDrive: Boolean,
)

/** Which small sheet is up over the evidence: S-SCO-03 in one of its three states. */
public enum class NeededStep { ASK, DONE, COUNT_AGAIN }

/** S-SCO-03 "Was it needed?" (F-SCO-05). [score] is the recomputed Smoothness on [NeededStep.DONE], when there is one. */
public data class NeededSheet(
    val step: NeededStep,
    val title: String,
    val body: String,
    val primary: String,
    val secondary: String?,
    val score: Int?,
    /** Decision D4 in words: the route-record cap on "needed" marks. */
    val capNote: String,
)

/** One "Not accurate?" answer. */
public data class EvidenceReason(val reason: InaccurateReason, val label: String)

/**
 * S-RPL-02 (F-RPL-04). [trace] is null when the R1 file is gone (EC-STO): [traceNote] then says so and the facts stay.
 *
 * @property position "Event 1 of 2", for the stepper.
 * @property reasonsOpen the inline "Not accurate?" reasons are showing; [selectedReason] is the chosen radio.
 */
public data class EvidenceState(
    val tripId: String,
    val eventId: String,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val type: EventType? = null,
    val needed: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val banners: List<EvidenceBanner> = emptyList(),
    val trace: EventEvidence? = null,
    val traceSummary: String = "",
    val facts: List<EvidenceFact> = emptyList(),
    val tip: EvidenceTip? = null,
    val position: String = "",
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val neededSheet: NeededSheet? = null,
    val reasonsOpen: Boolean = false,
    val reasons: List<EvidenceReason> = InaccurateReason.entries.map { EvidenceReason(it, EvidenceCopy.reason(it)) },
    val selectedReason: InaccurateReason? = null,
    val busy: Boolean = false,
    val accessibilityLabel: String = "",
    /** The chart's speed unit and the value it is converted with: "km/h" and 3.6. */
    val speedUnit: String = "km/h",
    val speedPerMps: Double = DisplayUnit.KM_PER_HOUR.perSi,
)

public sealed interface EvidenceIntent {
    /** "Was it needed?": S-SCO-03 asks. */
    public data object AskNeeded : EvidenceIntent

    /** "Yes, it was needed": the event leaves the score and the trip re-scores (F-SCO-05 AC1). */
    public data object ConfirmNeeded : EvidenceIntent

    /** The needed banner's "Count it again": S-SCO-03 asks to restore it. */
    public data object AskCountAgain : EvidenceIntent

    /** "Count it again": restored (F-SCO-05 AC3). */
    public data object ConfirmCountAgain : EvidenceIntent

    /** "No, count it", "Done" or "Keep it as needed": the small sheet closes with nothing more changed. */
    public data object DismissNeeded : EvidenceIntent

    public data object OpenReasons : EvidenceIntent

    public data class SelectReason(val reason: InaccurateReason) : EvidenceIntent

    /** "Send": stored on the device, the event is excluded and the sheet closes (F-RPL-04 AC4). */
    public data object SendReason : EvidenceIntent

    public data object CancelReasons : EvidenceIntent

    /** "‹": the previous event of the trip. */
    public data object Previous : EvidenceIntent

    /** "›": the next event of the trip. */
    public data object Next : EvidenceIntent

    /** "Why this tip": S-SCO-04. */
    public data object OpenTip : EvidenceIntent

    public data object Close : EvidenceIntent
}

public sealed interface EvidenceEffect {
    /** The sheet stepped to [eventId]: the replay underneath moves its marker and selection there. */
    public data class ShowEvent(val eventId: String) : EvidenceEffect

    /** Feedback on an event changed and the trip re-scored: the replay reads its pins again. */
    public data object FeedbackChanged : EvidenceEffect

    /** Open S-SCO-04 for [tipId] about [eventId]. */
    public data class OpenTip(val tripId: String, val tipId: String, val eventId: String) : EvidenceEffect

    /** A short confirmation: "Counted again · Smoothness recomputed". */
    public data class Toast(val text: String) : EvidenceEffect

    public data object Close : EvidenceEffect
}

/**
 * The shared S-RPL-02 event evidence sheet (F-RPL-04), with S-SCO-03 "Was it needed?" (F-SCO-05) over it. It reads the
 * event, its ±10 s evidence from R1 ([TripEvidence]), the trip's score for "This drive's tip", and writes feedback
 * through [MarkEventNeeded] and [ReportEventInaccurate], which re-score this trip and, through the feedback store,
 * every later one (§5.7).
 *
 * Analytics (keys and fixed values only): `evidence_opened {type}` per event shown, `tip_viewed {tip_id}` when the
 * related tip shows, `event_marked_needed {type, source: evidence}`, `event_unmarked {type}` and
 * `event_reported_inaccurate {type, reason}`.
 */
@Suppress("LongParameterList", "TooManyFunctions") // the sheet's inputs; one handler per intent
public class EvidencePresenter(
    private val tripId: String,
    eventId: String,
    private val trips: TripRepository,
    private val evidence: TripEvidence,
    private val markNeeded: MarkEventNeeded,
    private val reportInaccurate: ReportEventInaccurate,
    private val analytics: Analytics,
    private val units: Flow<UnitSystem>,
    private val format: ReplayTimeFormat,
    scope: CoroutineScope,
) : BasePresenter<EvidenceState, EvidenceIntent, EvidenceEffect>(EvidenceState(tripId, eventId), scope) {
    private var ownedScope: CoroutineScope? = null
    private var trip: Trip? = null
    private var events: Map<String, DrivingEvent> = emptyMap()
    private var markers: List<EventMarker> = emptyList()
    private var score: Score? = null
    private var unitSystem: UnitSystem = UnitSystem.METRIC

    init {
        this.scope.launch { load(eventId, report = true) }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    @Suppress("CyclomaticComplexMethod") // one branch per intent
    override fun dispatch(intent: EvidenceIntent) {
        val s = state.value
        if (s.loading || s.missing || s.busy) {
            if (intent == EvidenceIntent.Close) emit(EvidenceEffect.Close)
            return
        }
        when (intent) {
            EvidenceIntent.AskNeeded -> sheet(NeededStep.ASK)
            EvidenceIntent.AskCountAgain -> sheet(NeededStep.COUNT_AGAIN)
            EvidenceIntent.ConfirmNeeded -> setNeeded(true)
            EvidenceIntent.ConfirmCountAgain -> setNeeded(false)
            EvidenceIntent.DismissNeeded -> update { it.copy(neededSheet = null) }
            EvidenceIntent.OpenReasons -> update { it.copy(reasonsOpen = true, selectedReason = null) }
            is EvidenceIntent.SelectReason -> update { it.copy(selectedReason = intent.reason) }
            EvidenceIntent.SendReason -> sendReason()
            EvidenceIntent.CancelReasons -> update { it.copy(reasonsOpen = false, selectedReason = null) }
            EvidenceIntent.Previous -> step(-1)
            EvidenceIntent.Next -> step(1)
            EvidenceIntent.OpenTip -> s.tip?.let { emit(EvidenceEffect.OpenTip(tripId, it.tipId, s.eventId)) }
            EvidenceIntent.Close -> emit(EvidenceEffect.Close)
        }
    }

    private suspend fun load(eventId: String, report: Boolean) {
        unitSystem = units.first()
        val t = trips.trip(tripId)
        val feedback = trips.feedback(tripId)
        val all = trips.events(tripId).filter { feedback[it.id]?.inaccurate == null }
        trip = t
        events = all.associateBy { it.id }
        markers = EventMarkers.build(all, feedback, t?.quality?.spans.orEmpty())
        score = trips.score(tripId)
        val event = events[eventId]
        if (t == null || event == null) {
            update { it.copy(loading = false, missing = true) }
            return
        }
        show(event, feedback[eventId]?.needed == true, evidence.evidence(tripId, event))
        if (report) reportShown(event)
    }

    private fun reportShown(event: DrivingEvent) {
        analytics.evidenceEvent("evidence_opened", "type" to event.type.key)
        state.value.tip?.let { analytics.evidenceEvent("tip_viewed", "tip_id" to it.tipId) }
    }

    private fun show(event: DrivingEvent, needed: Boolean, trace: EventEvidence?) {
        val t = trip ?: return
        val index = markers.indexOfFirst { it.eventId == event.id }
        val speed = unitSystem.display(DisplayUnit.KM_PER_HOUR)
        val time = ReplayCopy.timeOfDay(format, t.startedAt, event.tPeakS)
        update {
            it.copy(
                eventId = event.id,
                loading = false,
                missing = false,
                type = event.type,
                needed = needed,
                title = ReplayCopy.eventTitle(event.type),
                subtitle = ReplayCopy.dot("${ReplayCopy.clock(event.tPeakS)} into the drive", time),
                banners = banners(event, needed, trace),
                trace = trace,
                traceSummary = traceSummary(event, trace),
                facts = facts(event, needed),
                tip = tip(event),
                position = if (index >= 0) "Event ${index + 1} of ${markers.size}" else "",
                hasPrevious = index > 0,
                hasNext = index >= 0 && index < markers.lastIndex,
                neededSheet = null,
                reasonsOpen = false,
                selectedReason = null,
                busy = false,
                accessibilityLabel = accessibilityLabel(event, time),
                speedUnit = speed.symbol,
                speedPerMps = speed.perSi,
            )
        }
    }

    private fun banners(event: DrivingEvent, needed: Boolean, trace: EventEvidence?): List<EvidenceBanner> = listOfNotNull(
        EvidenceBanner(EvidenceBannerKind.LOW_CONFIDENCE, EvidenceCopy.LOW_CONFIDENCE, null).takeIf { event.confidence == Confidence.LOW },
        EvidenceBanner(EvidenceBannerKind.NEEDED, EvidenceCopy.NEEDED, EvidenceCopy.COUNT_IT_AGAIN).takeIf { needed },
        EvidenceBanner(EvidenceBannerKind.TRACE_REMOVED, EvidenceCopy.TRACE_REMOVED, null).takeIf { trace == null },
    )

    private fun facts(event: DrivingEvent, needed: Boolean): List<EvidenceFact> {
        val speed = unitSystem.display(DisplayUnit.KM_PER_HOUR)
        val before = event.speedBeforeMps?.let(speed::format) ?: ReplayCopy.NO_VALUE
        val after = event.speedAfterMps?.let(speed::format) ?: ReplayCopy.NO_VALUE
        val peak = DisplayUnit.G.format(event.peakG)
        val duration = DisplayUnit.SECONDS.format(event.durationS)
        val confidence = EvidenceCopy.confidence(event.confidence)
        return listOf(
            fact("Peak", peak, "g", Metric.EVENT_PEAK_G, needed, "Peak $peak g"),
            fact("Duration", duration, "s", Metric.EVENT_DURATION, false, "Duration $duration seconds"),
            fact(
                "Speed", "$before → $after", speed.symbol, Metric.CURRENT_SPEED, false,
                "Speed from $before to $after ${ReplayCopy.spoken(speed.symbol)}",
            ),
            fact("Confidence", confidence, "", Metric.EVENT_CONFIDENCE, false, "Confidence $confidence"),
        )
    }

    private fun fact(label: String, value: String, unit: String, metric: Metric, struck: Boolean, spoken: String): EvidenceFact {
        val provenance = metric.provenance()
        val crossed = if (struck) ", not counted" else ""
        return EvidenceFact(label, value, unit, provenance, struck, "$spoken$crossed. ${provenance.label}.")
    }

    private fun tip(event: DrivingEvent): EvidenceTip? {
        val own = score?.tip?.takeIf { it.eventId == event.id }
        val tipId = own?.tipId ?: TipCopy.relatedTipId(event.type)
        val words = TipCopy.text(tipId) ?: return null
        val kicker = if (own != null) EvidenceCopy.THIS_DRIVES_TIP else EvidenceCopy.NEXT_TIME
        return EvidenceTip(tipId, kicker, words.title, words.nextTime, thisDrive = own != null)
    }

    private fun traceSummary(event: DrivingEvent, trace: EventEvidence?): String {
        if (trace == null) return EvidenceCopy.TRACE_REMOVED
        val speed = unitSystem.display(DisplayUnit.KM_PER_HOUR)
        val from = event.speedBeforeMps?.let(speed::format) ?: ReplayCopy.NO_VALUE
        val to = event.speedAfterMps?.let(speed::format) ?: ReplayCopy.NO_VALUE
        return "Speed and G from 10 seconds before to 10 seconds after. Speed went from $from to $to " +
            "${ReplayCopy.spoken(speed.symbol)}. Peak ${DisplayUnit.G.format(event.peakG)} g."
    }

    private fun accessibilityLabel(event: DrivingEvent, time: String): String {
        val speed = unitSystem.display(DisplayUnit.KM_PER_HOUR)
        val at = if (time.isEmpty()) "" else ", $time"
        val from = event.speedBeforeMps?.let(speed::format)
        val to = event.speedAfterMps?.let(speed::format)
        val speeds = if (from != null && to != null) ", from $from to $to ${ReplayCopy.spoken(speed.symbol)}" else ""
        return "${ReplayCopy.eventTitle(event.type)}$at. Peak ${DisplayUnit.G.format(event.peakG)} g over " +
            "${DisplayUnit.SECONDS.format(event.durationS)} seconds$speeds. " +
            "Confidence ${EvidenceCopy.confidence(event.confidence).lowercase()}."
    }

    private fun neededCount(): Int = markers.count { it.needed }

    private fun sheet(step: NeededStep, smoothness: Int? = null) {
        val type = state.value.type ?: return
        val marked = neededCount()
        val cap = if (marked > RECORD_CAP) EvidenceCopy.overRecordCap(marked) else EvidenceCopy.RECORD_CAP
        val sheet = when (step) {
            NeededStep.ASK -> NeededSheet(
                step, EvidenceCopy.WAS_IT_NEEDED, EvidenceCopy.askBody(type), EvidenceCopy.YES_NEEDED,
                EvidenceCopy.NO_COUNT_IT, null, cap,
            )
            NeededStep.DONE -> NeededSheet(
                step, EvidenceCopy.MARKED_NEEDED, EvidenceCopy.doneBody(type, smoothness), EvidenceCopy.DONE, null,
                smoothness, cap,
            )
            NeededStep.COUNT_AGAIN -> NeededSheet(
                step, EvidenceCopy.COUNT_AGAIN_TITLE, EvidenceCopy.COUNT_AGAIN_BODY, EvidenceCopy.COUNT_IT_AGAIN,
                EvidenceCopy.KEEP_NEEDED, null, cap,
            )
        }
        update { it.copy(neededSheet = sheet) }
    }

    private fun setNeeded(needed: Boolean) {
        val s = state.value
        val type = s.type ?: return
        update { it.copy(busy = true) }
        scope.launch {
            when (val result = markNeeded(tripId, s.eventId, needed)) {
                is Outcome.Success -> {
                    if (needed) {
                        analytics.evidenceEvent("event_marked_needed", "type" to type.key, "source" to SOURCE)
                    } else {
                        analytics.evidenceEvent("event_unmarked", "type" to type.key)
                    }
                    load(s.eventId, report = false)
                    emit(EvidenceEffect.FeedbackChanged)
                    if (needed) sheet(NeededStep.DONE, result.value?.smoothness) else emit(EvidenceEffect.Toast(EvidenceCopy.COUNTED_AGAIN))
                }
                is Outcome.Failure -> failed()
            }
        }
    }

    private fun sendReason() {
        val s = state.value
        val reason = s.selectedReason ?: return
        val type = s.type ?: return
        update { it.copy(busy = true) }
        scope.launch {
            when (reportInaccurate(tripId, s.eventId, reason)) {
                is Outcome.Success -> {
                    analytics.evidenceEvent("event_reported_inaccurate", "type" to type.key, "reason" to EvidenceCopy.reasonKey(reason))
                    update { it.copy(busy = false, reasonsOpen = false) }
                    emit(EvidenceEffect.FeedbackChanged)
                    emit(EvidenceEffect.Toast(EvidenceCopy.THANKS))
                    emit(EvidenceEffect.Close)
                }
                is Outcome.Failure -> failed()
            }
        }
    }

    private fun failed() {
        update { it.copy(busy = false, neededSheet = null) }
        emit(EvidenceEffect.Toast(EvidenceCopy.SAVE_FAILED))
    }

    private fun step(by: Int) {
        val current = state.value.eventId
        val i = markers.indexOfFirst { it.eventId == current }
        val next = markers.getOrNull(i + by)?.takeIf { i >= 0 } ?: return
        update { it.copy(busy = true) }
        scope.launch {
            load(next.eventId, report = true)
            emit(EvidenceEffect.ShowEvent(next.eventId))
        }
    }

    public companion object {
        /** D4: more than this many "needed" marks and a run cannot set a route record. */
        public const val RECORD_CAP: Int = 2

        /** The `source` of `event_marked_needed` from this sheet. */
        private const val SOURCE = "evidence"

        /** An evidence sheet on the main thread in a scope of its own, for Swift. Call [close] on deinit. */
        @Suppress("LongParameterList") // the constructor's inputs, minus the scope
        public fun onMain(
            tripId: String,
            eventId: String,
            trips: TripRepository,
            evidence: TripEvidence,
            markNeeded: MarkEventNeeded,
            reportInaccurate: ReportEventInaccurate,
            analytics: Analytics,
            units: Flow<UnitSystem>,
            format: ReplayTimeFormat,
        ): EvidencePresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return EvidencePresenter(tripId, eventId, trips, evidence, markNeeded, reportInaccurate, analytics, units, format, scope)
                .also { it.ownedScope = scope }
        }
    }
}

/** The `type` value of the evidence events. */
internal val EventType.key: String get() = name.lowercase()

internal fun Analytics.evidenceEvent(name: String, vararg properties: Pair<String, String>) {
    track(EvidenceEvent(name, properties.toMap()))
}

/** An evidence, feedback or tip analytics event (PRD F-RPL-04, F-SCO-05, F-SCO-06): fixed keys and values only. */
public data class EvidenceEvent(override val name: String, override val properties: Map<String, String>) : AnalyticsEvent
