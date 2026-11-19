package com.driveperf.domain.replay

import com.driveperf.domain.trip.SegmentReason

/**
 * How a stretch of the route is drawn (F-RPL-08 AC1). Both apps map these to the same strokes: [OBSERVED] solid,
 * [ESTIMATED] dashed, [NO_GPS] broken (no line, or a faint dotted connector), [EXCLUDED] hatched or dimmed.
 */
public enum class SegmentStyle {
    /** Measured by gated GNSS fixes. */
    OBSERVED,

    /** The estimated start (F-CAP-05): a straight line from the parking spot to the first fix. Never scored (D6). */
    ESTIMATED,

    /** No position: a GNSS gap, a tunnel or the app being killed. The marker jumps it and is never interpolated. */
    NO_GPS,

    /** Measured, but left out of the score (phone moved, not aligned, low IMU rate or an IMU gap). */
    EXCLUDED,
}

/** One position of the route, on the trip time base (seconds from `Trip.startedAt`). [speedMps] is gated GNSS speed. */
public data class ReplayPoint(val tS: Double, val lat: Double, val lng: Double, val speedMps: Double?)

/** A run of the route drawn in one [style]. Neighbouring segments share their boundary point, so the line is continuous. */
public data class ReplaySegment(val style: SegmentStyle, val points: List<ReplayPoint>)

/**
 * A quality overlay span `[t0S, t1S)` on the timeline, in seconds from `Trip.startedAt`. Only non-[SegmentStyle.OBSERVED]
 * styles have spans. [reason] is why an [SegmentStyle.EXCLUDED] span was left out; null for the other styles and when
 * unknown.
 */
public data class ReplaySpan(val style: SegmentStyle, val t0S: Double, val t1S: Double, val reason: SegmentReason?) {
    public constructor(style: SegmentStyle, t0S: Double, t1S: Double) : this(style, t0S, t1S, null)

    /** The chip the marker shows inside this span (F-RPL-08 AC2), or null for [SegmentStyle.OBSERVED]. */
    public val label: String?
        get() = when (style) {
            SegmentStyle.OBSERVED -> null
            SegmentStyle.ESTIMATED -> LABEL_ESTIMATED
            SegmentStyle.NO_GPS -> LABEL_NO_GPS
            SegmentStyle.EXCLUDED -> if (reason == null || reason == SegmentReason.PHONE_MOVED) LABEL_PHONE_MOVED else LABEL_NOT_SCORED
        }

    public companion object {
        /** Chip copy from the S-RPL-01 design spec (DRIVE-61). */
        public const val LABEL_ESTIMATED: String = "Estimated start"
        public const val LABEL_NO_GPS: String = "No GPS"
        public const val LABEL_PHONE_MOVED: String = "Phone moved — not scored"

        /** For spans left out for other reasons (not aligned, low IMU rate, IMU gap); the legend's "Not scored". */
        public const val LABEL_NOT_SCORED: String = "Not scored"
    }
}

/**
 * Where the marker is at [tS] (display only, F-RPL-01). [speedMps] and [bearingDeg] are null where they are unknown:
 * in a [SegmentStyle.NO_GPS] gap, on the estimated start (no measured speed), or before the car first moved (bearing).
 * [bearingDeg] is clockwise from true north, `[0, 360)`.
 */
public data class ReplayFrame(
    val tS: Double,
    val lat: Double,
    val lng: Double,
    val speedMps: Double?,
    val bearingDeg: Double?,
    val style: SegmentStyle,
)

/** Playback speeds (F-RPL-01 AC2). [AUTO] is the default and plays the whole trip in about 60 s. */
public enum class PlaybackSpeed { AUTO, X1, X10, X30 }

/** The Replay of one trip (F-RPL-01). Null when the trip does not exist, is not finished, or has no position at all. */
public interface ReplayRepository {
    public suspend fun replay(tripId: String): Replay?
}
