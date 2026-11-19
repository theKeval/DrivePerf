package com.driveperf.presentation.evidence

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.replay.TripEvidence
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DisplayUnit
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.Trip
import com.driveperf.presentation.BasePresenter
import com.driveperf.presentation.replay.ReplayCopy
import com.driveperf.presentation.score.TipCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * S-SCO-04 "One thing for next time" (F-SCO-06). [noTip] replaces everything else on a trip with no fair tip
 * (unscorable, passenger, not a drive): its words say why.
 *
 * @property kicker "This drive's tip" for the trip's own tip, "Next time" for a library tip opened from another event.
 * @property tipTitle and [tipText] are null until the tip library's words exist ([TipCopy.text]); the hero card hides.
 * @property sparkline the event's longitudinal G over its ±10 s window (peak at [sparklinePeakIndex]), or empty when
 *   the trace was not kept or the tip has no event.
 * @property eventId the event "See the moment" opens, or null for a tip with no event.
 */
public data class TipDetailState(
    val tripId: String,
    val loading: Boolean = true,
    val title: String = EvidenceCopy.TIP_TITLE,
    val tipId: String? = null,
    val cleanest: String? = null,
    val kicker: String = "",
    val tipTitle: String? = null,
    val tipText: String? = null,
    val why: String = "",
    val sparkline: List<Double> = emptyList(),
    val sparklinePeakIndex: Int = -1,
    val eventId: String? = null,
    val steps: List<String> = emptyList(),
    val footer: String = EvidenceCopy.TIP_FOOTER,
    val noTip: String? = null,
)

public sealed interface TipDetailIntent {
    /** "See the moment": S-RPL-02 for the tip's event. */
    public data object SeeTheMoment : TipDetailIntent

    public data object Back : TipDetailIntent
}

public sealed interface TipDetailEffect {
    public data class OpenEvidence(val tripId: String, val eventId: String) : TipDetailEffect

    public data object Close : TipDetailEffect
}

/**
 * The shared S-SCO-04 tip detail (F-SCO-06, DRIVE-102). With no [tipId] it shows the trip's own tip; with a [tipId]
 * and an [eventId] that is not the trip tip's event it shows that library tip as "Next time", about that event.
 * Places are left out: there is no geocoder in v1 (sprint 4 decision 2).
 *
 * Analytics: `tip_viewed {tip_id}` once shown and `tip_evidence_opened {tip_id}` on "See the moment".
 */
@Suppress("LongParameterList") // the screen's inputs
public class TipDetailPresenter(
    private val tripId: String,
    private val tipId: String?,
    private val eventId: String?,
    private val trips: TripRepository,
    private val evidence: TripEvidence,
    private val analytics: Analytics,
    private val units: Flow<UnitSystem>,
    scope: CoroutineScope,
) : BasePresenter<TipDetailState, TipDetailIntent, TipDetailEffect>(TipDetailState(tripId), scope) {
    private var ownedScope: CoroutineScope? = null

    init {
        this.scope.launch { load() }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    override fun dispatch(intent: TipDetailIntent) {
        when (intent) {
            TipDetailIntent.SeeTheMoment -> {
                val s = state.value
                val event = s.eventId ?: return
                s.tipId?.let { analytics.evidenceEvent("tip_evidence_opened", "tip_id" to it) }
                emit(TipDetailEffect.OpenEvidence(tripId, event))
            }
            TipDetailIntent.Back -> emit(TipDetailEffect.Close)
        }
    }

    /** Which tip to show and about which event: the trip's own, or the library tip asked for. */
    private class Pick(val tipId: String, val mine: Boolean, val event: DrivingEvent?, val category: TipCategory?)

    private suspend fun pick(): Pick? {
        val score = trips.score(tripId)
        val own = score?.tip?.takeIf { score.smoothness != null }
        val events = trips.events(tripId).associateBy { it.id }
        val asked = eventId?.let(events::get)
        val ownTip = own?.takeIf { (tipId == null || tipId == it.tipId) && (asked == null || asked.id == it.eventId) }
        return when {
            ownTip != null -> Pick(ownTip.tipId, true, ownTip.eventId?.let(events::get), ownTip.category)
            tipId != null && asked != null -> Pick(tipId, false, asked, null)
            else -> null
        }
    }

    private suspend fun load() {
        val unitSystem = units.first()
        val trip = trips.trip(tripId)
        val pick = trip?.let { pick() }
        if (trip == null || pick == null) {
            update { it.copy(loading = false, noTip = EvidenceCopy.noTipBody(noTipReason(trip))) }
            return
        }
        val id = pick.tipId
        val mine = pick.mine
        val event = pick.event
        val words = TipCopy.text(id)
        val category = words?.category ?: pick.category ?: event?.let { EvidenceCopy.category(it.type) } ?: TipCategory.BRAKING
        val window = event?.let { evidence.evidence(tripId, it) }
        val spark = window?.g?.map { it.lonG }.orEmpty()
        update {
            it.copy(
                loading = false,
                tipId = id,
                cleanest = trip.cleanest?.let { c -> cleanest(c.distanceM, unitSystem) },
                kicker = if (mine) EvidenceCopy.THIS_DRIVES_TIP else EvidenceCopy.NEXT_TIME,
                tipTitle = words?.title,
                tipText = words?.nextTime,
                why = event?.let { e -> why(e, mine, unitSystem) } ?: EvidenceCopy.WHY_NO_EVENT,
                sparkline = spark,
                sparklinePeakIndex = window?.g?.indexOfFirst { s -> s.tS >= window.tPeakS } ?: -1,
                eventId = event?.id,
                steps = EvidenceCopy.steps(category),
            )
        }
        analytics.evidenceEvent("tip_viewed", "tip_id" to id)
    }

    private fun cleanest(distanceM: Double, units: UnitSystem): String {
        val km = units.display(DisplayUnit.KILOMETRES)
        return "${km.format(distanceM)} ${km.symbol} with no abrupt inputs."
    }

    private fun why(event: DrivingEvent, mine: Boolean, units: UnitSystem): String {
        val speed = units.display(DisplayUnit.KM_PER_HOUR)
        val from = event.speedBeforeMps?.let(speed::format) ?: ReplayCopy.NO_VALUE
        val to = event.speedAfterMps?.let(speed::format) ?: ReplayCopy.NO_VALUE
        val where = if (mine) "on this drive" else "around this moment"
        return "It’s the moment that cost the most smoothness $where: ${ReplayCopy.eventTitle(event.type).lowercase()}, " +
            "peaking at ${DisplayUnit.G.format(event.peakG)} g from $from to $to ${speed.symbol}."
    }

    /** The reason in the no-tip sentence, lower case: "precise location was off". */
    private fun noTipReason(trip: Trip?): String = when (trip?.quality?.reason) {
        QualityReason.NO_IMU -> "no motion sensor data reached the app"
        QualityReason.NO_PRECISE_LOCATION -> "precise location was off"
        QualityReason.NOT_ALIGNED -> "we couldn’t work out which way your phone faced"
        QualityReason.LOW_SCORED_SHARE -> "too much of the drive had to be left out"
        QualityReason.TOO_LITTLE_SCORED_TIME -> "too little of the drive could be scored"
        QualityReason.LOW_GNSS -> "GPS was weak for much of the drive"
        QualityReason.NO_GYRO -> "this phone has no gyroscope"
        QualityReason.LOW_IMU_RATE -> "the sensors ran slower than usual"
        null -> "not enough good data"
    }

    public companion object {
        /** A tip detail on the main thread in a scope of its own, for Swift. Call [close] on deinit. */
        @Suppress("LongParameterList") // the constructor's inputs, minus the scope
        public fun onMain(
            tripId: String,
            tipId: String?,
            eventId: String?,
            trips: TripRepository,
            evidence: TripEvidence,
            analytics: Analytics,
            units: Flow<UnitSystem>,
        ): TipDetailPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return TipDetailPresenter(tripId, tipId, eventId, trips, evidence, analytics, units, scope).also { it.ownedScope = scope }
        }
    }
}
