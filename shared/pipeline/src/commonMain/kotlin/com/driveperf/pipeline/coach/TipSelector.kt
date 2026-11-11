package com.driveperf.pipeline.coach

import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import kotlin.math.floor

/** The 5 s window of most excess jerk when no event qualifies (§6.2), on the trip time base. */
public data class JerkWindow(val category: TipCategory, val t0S: Double, val t1S: Double)

/** Everything §6.2 needs. Positions resolve a span anchor to the nearest gated fix. */
public data class TipInputs(
    val score: Score,
    val events: List<DrivingEvent>,
    val feedback: Map<String, EventFeedback>,
    val context: DriveContext,
    val stopStartCount: Int,
    /** Tip IDs of the last 2 scorable trips, newest first. */
    val recentTipIds: List<String>,
    /** Null when R1 is unavailable (re-selection after feedback without the trace). */
    val jerkWindow: JerkWindow?,
    val cleanestMidS: Double?,
    val positionAt: (Double) -> Pair<Double, Double>?,
    val eventCost: (DrivingEvent) -> Double,
)

/**
 * Coaching tip selection (§6): exactly one deterministic, explainable tip per scorable trip, tied to a place. Tips never
 * mention speed targets. Copy is owned by A2/A6; this only chooses the `tipId`.
 */
public object TipSelector {
    /** The 14 launch tips in table order per category (§6.1). */
    public val LIBRARY: Map<TipCategory, List<String>> = mapOf(
        TipCategory.BRAKING to listOf("brake_light", "brake_ease_end", "brake_longer_ramp"),
        TipCategory.ACCELERATION to listOf("accel_progressive", "accel_first_second", "accel_merge_build"),
        TipCategory.CORNERING to listOf("corner_brake_before", "corner_smooth_entry", "corner_unwind_exit"),
        TipCategory.CONSISTENCY to listOf("consist_hold_steady", "consist_longer_look"),
        TipCategory.ANTICIPATION to listOf("anticip_read_ahead", "anticip_coast_red", "anticip_leave_gap"),
    )

    private const val STOPPED_MPS = 0.5
    private const val EASE_END_S = 1.5
    private const val PULL_AWAY_MPS = 2.0
    private const val CORNER_AFTER_BRAKE_S = 3.0
    private const val STOP_STARTS = 4
    private const val HUNDRED = 100.0
    private const val TEN = 10.0
    private const val PLACE_DECIMALS = 1e4

    public fun select(i: TipInputs): Tip {
        val active = i.events.filter { e -> i.feedback[e.id]?.let { it.needed || it.inaccurate != null } != true }
        val candidates = active.filter { it.confidence != Confidence.LOW }
        val costs = categoryCosts(i.score)
        val order = TipCategory.entries
        val (category, anchor, spanMid) = when {
            candidates.isNotEmpty() -> {
                val withCandidate = order.filter { c -> candidates.any { categoryOf(it.type) == c } }
                val category = withCandidate.maxWith(compareBy<TipCategory> { costs[it] ?: 0.0 }.thenByDescending { order.indexOf(it) })
                Triple(category, anchorOf(candidates.filter { categoryOf(it.type) == category }, i.eventCost), null)
            }
            i.jerkWindow != null -> Triple(i.jerkWindow.category, null, (i.jerkWindow.t0S + i.jerkWindow.t1S) / 2)
            else -> Triple(TipCategory.ANTICIPATION, null, i.cleanestMidS)
        }
        val preferred = ruleChoice(category, anchor, active, i)
        val table = LIBRARY.getValue(category)
        val ordered = listOf(preferred) + table.filter { it != preferred }
        val tipId = ordered.firstOrNull { it !in i.recentTipIds } ?: table.first()
        val place = anchor?.let { e -> e.lat?.let { lat -> e.lng?.let { lng -> lat to lng } } } ?: spanMid?.let(i.positionAt)
        return Tip(tipId, category, anchor?.id, place?.let { placeKey(it.first, it.second) })
    }

    /** §6.2 category cost: `R_i` for the event channels, `(100 − sub) / 10` for consistency. */
    private fun categoryCosts(score: Score): Map<TipCategory, Double> = score.contributors.associate { c ->
        when (c.id) {
            ContributorId.BRAKING -> TipCategory.BRAKING to (c.value ?: 0.0)
            ContributorId.ACCELERATION -> TipCategory.ACCELERATION to (c.value ?: 0.0)
            ContributorId.CORNERING -> TipCategory.CORNERING to (c.value ?: 0.0)
            ContributorId.CONSISTENCY -> TipCategory.CONSISTENCY to (HUNDRED - c.subScore) / TEN
        }
    }

    /** The largest-cost event with a position (tie earliest); without positions, the earliest. */
    private fun anchorOf(events: List<DrivingEvent>, cost: (DrivingEvent) -> Double): DrivingEvent {
        val pinned = events.filter { it.lat != null && it.lng != null }
        return pinned.sortedWith(compareByDescending<DrivingEvent> { cost(it) }.thenBy { it.tPeakS }).firstOrNull()
            ?: events.minBy { it.tPeakS }
    }

    @Suppress("CyclomaticComplexMethod") // the §6.2 rule table, first match wins
    private fun ruleChoice(category: TipCategory, anchor: DrivingEvent?, active: List<DrivingEvent>, i: TipInputs): String =
        when (category) {
            TipCategory.BRAKING -> when {
                anchor != null && (anchor.speedAfterMps ?: Double.MAX_VALUE) < STOPPED_MPS -> "brake_light"
                anchor != null && anchor.tEndS - anchor.tPeakS <= EASE_END_S -> "brake_ease_end"
                else -> "brake_longer_ramp"
            }
            TipCategory.ACCELERATION -> when {
                anchor != null && (anchor.speedBeforeMps ?: Double.MAX_VALUE) < PULL_AWAY_MPS -> "accel_first_second"
                i.context == DriveContext.HIGHWAY -> "accel_merge_build"
                else -> "accel_progressive"
            }
            TipCategory.CORNERING -> when {
                anchor != null && active.any { b ->
                    b.type == EventType.BRAKE && b.tEndS <= anchor.tStartS && anchor.tStartS - b.tEndS <= CORNER_AFTER_BRAKE_S
                } -> "corner_brake_before"
                anchor != null && anchor.tPeakS < (anchor.tStartS + anchor.tEndS) / 2 -> "corner_smooth_entry"
                else -> "corner_unwind_exit"
            }
            TipCategory.CONSISTENCY -> if (i.context == DriveContext.HIGHWAY) "consist_hold_steady" else "consist_longer_look"
            TipCategory.ANTICIPATION -> when {
                i.stopStartCount >= STOP_STARTS -> "anticip_coast_red"
                i.context == DriveContext.CITY -> "anticip_leave_gap"
                else -> "anticip_read_ahead"
            }
        }

    private fun categoryOf(type: EventType): TipCategory = when (type) {
        EventType.BRAKE -> TipCategory.BRAKING
        EventType.ACCEL -> TipCategory.ACCELERATION
        EventType.CORNER -> TipCategory.CORNERING
    }

    /** A place key about 11 m wide. Device data only: never sent to analytics (data-model.md P10). */
    private fun placeKey(lat: Double, lng: Double): String = "p:${round4(lat)},${round4(lng)}"

    private fun round4(x: Double): String {
        val n = floor(x * PLACE_DECIMALS + 0.5).toLong()
        val sign = if (n < 0) "-" else ""
        val a = kotlin.math.abs(n)
        return "$sign${a / PLACE_DECIMALS.toLong()}.${(a % PLACE_DECIMALS.toLong()).toString().padStart(4, '0')}"
    }
}
