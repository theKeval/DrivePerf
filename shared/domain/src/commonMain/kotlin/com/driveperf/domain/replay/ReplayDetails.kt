package com.driveperf.domain.replay

import com.driveperf.domain.trip.DrivingEvent

/**
 * The max-speed pin (F-RPL-02 AC2): the highest speed with a good GPS fix, at its place. Neutral everywhere (D5/D6).
 * [tS] is seconds from the trip start.
 */
public data class MaxSpeedPin(val tS: Double, val speedMps: Double, val lat: Double, val lng: Double)

/**
 * One stop of at least 10 s (F-RPL-02 AC3, A12). [number] counts from 1 in time order. [lat]/[lng] are null when no
 * gated fix was near: the stop is counted but not pinned.
 */
public data class StopPin(val number: Int, val atS: Double, val durationS: Double, val lat: Double?, val lng: Double?) {
    /** It gets a map pin. */
    public val pinned: Boolean get() = lat != null && lng != null
}

/** One elevation sample on the trip time base. */
public data class ElevationPoint(val tS: Double, val elevM: Double)

/**
 * The mini elevation profile under the scrubber (F-RPL-02 AC4), from the preview track's smoothed GNSS altitude (D8:
 * no barometer). [gainM] and [lossM] are the trip's totals (changes under 5 m ignored).
 */
public class ElevationProfile(public val points: List<ElevationPoint>, public val gainM: Double, public val lossM: Double) {
    init {
        require(points.isNotEmpty()) { "an elevation profile needs at least one point" }
    }

    public val minM: Double = points.minOf { it.elevM }
    public val maxM: Double = points.maxOf { it.elevM }

    /** The elevation at [tS], interpolated between samples and held flat outside them. */
    public fun elevationAt(tS: Double): Double {
        val i = points.indexOfFirst { it.tS > tS }
        return when (i) {
            -1 -> points.last().elevM
            0 -> points.first().elevM
            else -> {
                val a = points[i - 1]
                val b = points[i]
                a.elevM + (b.elevM - a.elevM) * (tS - a.tS) / (b.tS - a.tS)
            }
        }
    }

    /** Where [tS]'s elevation sits between [minM] (0) and [maxM] (1), for drawing the cursor; 0.5 on a flat drive. */
    public fun heightFractionAt(tS: Double): Double =
        if (maxM > minM) (elevationAt(tS) - minM) / (maxM - minM) else HALF

    private companion object {
        const val HALF = 0.5
    }
}

/**
 * Distance so far along the replay route, for the readout (F-RPL-02 AC1). It integrates the track and scales it to the
 * trip's own distance, so the readout ends on the number the trip shows. Gaps are counted as the straight line the
 * marker jumps.
 */
public class ReplayDistance(track: List<ReplayPoint>, tripDistanceM: Double?) {
    private val times = DoubleArray(track.size) { track[it].tS }
    private val metres = DoubleArray(track.size)

    init {
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            metres[i] = metres[i - 1] + RouteGeo.distanceM(a.lat, a.lng, b.lat, b.lng)
        }
        val integrated = metres.lastOrNull() ?: 0.0
        val scale = if (tripDistanceM != null && tripDistanceM > 0 && integrated > 0) tripDistanceM / integrated else 1.0
        for (i in metres.indices) metres[i] *= scale
    }

    /** The whole route in metres. */
    public val totalM: Double get() = metres.lastOrNull() ?: 0.0

    /** Metres driven by [tS], interpolated between track points. Allocation-free, so it can run every frame. */
    public fun at(tS: Double): Double = when {
        times.isEmpty() || tS <= times[0] -> 0.0
        tS >= times[times.size - 1] -> totalM
        else -> between(tS)
    }

    private fun between(tS: Double): Double {
        var lo = 0
        var hi = times.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= tS) lo = mid else hi = mid
        }
        val f = (tS - times[lo]) / (times[hi] - times[lo])
        return metres[lo] + (metres[hi] - metres[lo]) * f
    }
}

/**
 * What Replay draws on top of the route (F-RPL-02, F-RPL-03): event pins and timeline ticks, the max-speed pin, stop
 * pins, the elevation profile and the distance readout. Built once per load, and again after feedback changes.
 *
 * @property markers every event the user has not reported as inaccurate, by peak time; "Needed" ones are hollow.
 * @property events the same events by ID, for the confidence `event_pin_tapped` sends.
 * @property stops every stop in time order; [StopPin.pinned] ones get a pin.
 * @property startedAtMs the trip start (UTC epoch ms), for times of day.
 */
@Suppress("LongParameterList") // one property per thing drawn, built by TripReplayDetails
public class ReplayDetails(
    public val markers: List<EventMarker>,
    public val events: Map<String, DrivingEvent>,
    public val maxSpeed: MaxSpeedPin?,
    public val stops: List<StopPin>,
    public val elevation: ElevationProfile?,
    public val distance: ReplayDistance,
    public val startedAtMs: Long,
) {
    /** Events that count in the score: not marked "Needed" (F-RPL-03 AC4). */
    public val countedEvents: Int get() = markers.count { !it.needed }

    /** The marker for [eventId], or null. */
    public fun marker(eventId: String): EventMarker? = markers.firstOrNull { it.eventId == eventId }

    /**
     * Markers whose peak the playhead passed going from [fromS] to [toS]: `fromS < tPeakS ≤ toS`. Each pin pulses once
     * as the marker passes it (F-RPL-03 AC2); a backwards move or a jump of more than [maxJumpS] (a scrub) passes none.
     */
    public fun crossed(fromS: Double, toS: Double, maxJumpS: Double = MAX_PULSE_JUMP_S): List<EventMarker> =
        if (toS <= fromS || toS - fromS > maxJumpS) emptyList() else markers.filter { it.tPeakS > fromS && it.tPeakS <= toS }

    /** The marker before or after [eventId] in time order, for the evidence sheet's "‹ Event 1 of 2 ›". */
    public fun neighbour(eventId: String, step: Int): EventMarker? {
        val i = markers.indexOfFirst { it.eventId == eventId }
        return if (i < 0) null else markers.getOrNull(i + step)
    }

    public companion object {
        /**
         * The longest playhead step that still pulses the pins it passes: 30× playback moves about half a second of
         * trip per frame, Auto on a long trip a few seconds. A bigger step is a scrub or a seek.
         */
        public const val MAX_PULSE_JUMP_S: Double = 120.0
    }
}

/** What Replay draws on top of the route for a trip, or null when the trip is gone. */
public interface ReplayDetailsRepository {
    public suspend fun details(tripId: String, replay: Replay): ReplayDetails?
}

/** For previews and fixtures that have no details: nothing on top of the route. */
public object NoReplayDetails : ReplayDetailsRepository {
    override suspend fun details(tripId: String, replay: Replay): ReplayDetails? = null
}

/** A [ReplayDetailsRepository] that always answers with one prepared [details], for previews and UI-test fixtures. */
public class StaticReplayDetails(private val details: ReplayDetails?) : ReplayDetailsRepository {
    override suspend fun details(tripId: String, replay: Replay): ReplayDetails? = details
}
