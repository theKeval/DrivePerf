package com.driveperf.domain.trip

/** Phone state as S-DQ-01 shows it (F-DQ-04 AC2): mounted, loose or moved. */
public enum class PhoneState {
    /** Held still in a mount the whole time. */
    MOUNTED,

    /** Lying loose (a cupholder, a pocket, a bag): fair data, with a little more noise. */
    LOOSE,

    /** Moved during the drive: those minutes were left out of the score. */
    MOVED,

    /** Not enough data to tell. */
    UNKNOWN,
}

/** Phone state: [PhoneState.MOVED] when any span was left out because the phone moved, else from [TripQuality.placement]. */
public val TripQuality.phoneState: PhoneState
    get() = when {
        spans.any { it.kind == SegmentKind.EXCLUDED && it.reason == SegmentReason.PHONE_MOVED } -> PhoneState.MOVED
        placement == Placement.MOUNTED -> PhoneState.MOUNTED
        placement == Placement.UNKNOWN -> PhoneState.UNKNOWN
        else -> PhoneState.LOOSE
    }

/** Time left out of the score for one [reason] (S-DQ-01 "Left out of the score"). Overlapping spans count once. */
public data class LeftOut(val reason: SegmentReason, val seconds: Double)

/**
 * What was left out of the score and why, longest first (F-DQ-04 AC2). Covers excluded and interrupted spans,
 * including the `GAP` spans with reason `APP_KILLED` that a recovered trip gets (§1.7); the estimated start and GNSS
 * gaps are shown on their own rows.
 */
public fun TripQuality.leftOut(): List<LeftOut> =
    spans.filter { it.isLeftOut }
        .groupBy { it.reason ?: SegmentReason.APP_KILLED }
        .map { (reason, list) -> LeftOut(reason, unionSeconds(list)) }
        .filter { it.seconds > 0 }
        .sortedWith(compareByDescending<LeftOut> { it.seconds }.thenBy { it.reason.ordinal })

/** How a stretch of the track is drawn on maps, timelines and the coverage strip (F-DQ-03, F-CAP-05, F-CAP-06). */
public enum class OverlayStyle {
    /** The estimated start (F-CAP-05): dashed and labelled "Estimated"; never scored, never pinned. */
    ESTIMATED_START,

    /** A GNSS gap under 3 min while moving (F-DQ-03 AC2): bridged with a dashed "Estimated" connector. */
    SHORT_GAP,

    /** A GNSS gap of 3 min or more (F-DQ-03 AC2): a break in the line with a "No GPS" label. */
    LONG_GAP,

    /** Measured but left out of the score (phone moved, not aligned, low sensor rate): hatched. */
    EXCLUDED,

    /** The app was stopped and nothing was recorded (F-CAP-06 AC3): a gap, never filled and never scored. */
    INTERRUPTED,

    /** The user paused recording. */
    PAUSED,
}

/** One stretch to draw differently, in seconds from the trip start. [provenance] is set for drawn estimates. */
public data class TrackOverlay(val style: OverlayStyle, val t0S: Double, val t1S: Double, val reason: SegmentReason?) {
    public val provenance: Provenance?
        get() = when (style) {
            OverlayStyle.ESTIMATED_START, OverlayStyle.SHORT_GAP -> Provenance.ESTIMATED
            OverlayStyle.EXCLUDED -> Provenance.OBSERVED
            OverlayStyle.LONG_GAP, OverlayStyle.INTERRUPTED, OverlayStyle.PAUSED -> null
        }

    /** True when the stretch never contributes to a score. */
    public val notScored: Boolean get() = style != OverlayStyle.SHORT_GAP && style != OverlayStyle.LONG_GAP
}

/**
 * The trip's quality spans as drawing instructions, in time order (F-DQ-03 "expose gaps and estimated segments").
 * The 3-minute line between short and long gaps is the pipeline's: it labels long gaps `gap.labelled` (§2.7).
 */
public fun TripQuality.overlays(): List<TrackOverlay> = spans.map { s ->
    val style = when (s.kind) {
        SegmentKind.ESTIMATED -> OverlayStyle.ESTIMATED_START
        SegmentKind.EXCLUDED -> OverlayStyle.EXCLUDED
        SegmentKind.INTERRUPTED -> OverlayStyle.INTERRUPTED
        SegmentKind.PAUSED -> OverlayStyle.PAUSED
        SegmentKind.GAP -> when {
            s.reason == SegmentReason.APP_KILLED -> OverlayStyle.INTERRUPTED
            s.labelKey == LONG_GAP_LABEL -> OverlayStyle.LONG_GAP
            else -> OverlayStyle.SHORT_GAP
        }
    }
    TrackOverlay(style, s.t0S, s.t1S, s.reason)
}.sortedWith(compareBy<TrackOverlay> { it.t0S }.thenBy { it.style.ordinal })

/** Excluded or interrupted: measured or missing time that never counts towards the score. */
private val TripSegment.isLeftOut: Boolean
    get() = kind == SegmentKind.EXCLUDED || kind == SegmentKind.INTERRUPTED ||
        (kind == SegmentKind.GAP && reason == SegmentReason.APP_KILLED)

/** The label key the pipeline puts on GNSS gaps of 3 min or more (pipeline-and-scoring.md §2.7). */
public const val LONG_GAP_LABEL: String = "gap.labelled"

/** Total length of the union of half-open spans. */
private fun unionSeconds(spans: List<TripSegment>): Double {
    var total = 0.0
    var end = Double.NEGATIVE_INFINITY
    for (s in spans.sortedBy { it.t0S }) {
        val from = maxOf(s.t0S, end)
        if (s.t1S > from) total += s.t1S - from
        end = maxOf(end, s.t1S)
    }
    return total
}
