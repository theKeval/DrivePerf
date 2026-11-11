package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.trip.Stop
import com.driveperf.foundation.Units

/** A capture cut in two at a long stop (§1.6). [firstEndNs] ends the first part; the second starts at its header. */
internal data class SplitCapture(val first: CapturedTrip, val firstEndNs: Long, val second: CapturedTrip)

/**
 * §1.6 "Trip > 12 h": at finalisation such a trip is split at its longest stop of more than 30 min (tie: earliest). The
 * first part ends where the stop begins and keeps the trip ID; the second starts where the stop ends, with a new ID and
 * the first part's end as its parking spot. Samples inside the stop belong to neither part: the car was parked.
 */
internal object TripSplit {
    /** The stop to split at, or null when there is none longer than [minStopS]. */
    fun stopFor(stops: List<Stop>, minStopS: Double): Stop? =
        stops.filter { it.durationS > minStopS }.sortedBy { it.atS }.maxByOrNull { it.durationS }

    /**
     * Cuts [capture] at [stop], whose times are seconds from [t0Ns] (the trip's `startedAt` on the monotonic clock).
     * [secondId] is the new trip's ID. The second part's parking spot is the first part's last fix.
     */
    fun split(capture: CapturedTrip, t0Ns: Long, stop: Stop, secondId: String): SplitCapture {
        val h = capture.header
        val cutNs = t0Ns + (stop.atS * Units.NANOS_PER_SECOND).toLong()
        val resumeNs = t0Ns + ((stop.atS + stop.durationS) * Units.NANOS_PER_SECOND).toLong()
        val parking = capture.fixes.lastOrNull { it.tMonoNs < cutNs }?.let { Parking(GeoPoint(it.lat, it.lng), it.wallMs) }
        val first = capture.copy(
            fixes = capture.fixes.filter { it.tMonoNs < cutNs },
            motion = capture.motion.filter { it.tMonoNs < cutNs },
            activity = capture.activity.filter { it.tMonoNs < cutNs },
            marks = capture.marks.filter { it.tMonoNs < cutNs },
        )
        val second = CapturedTrip(
            header = CaptureHeader(
                tripId = secondId,
                startMonoNs = resumeNs,
                startWallMs = h.startWallMs + (resumeNs - h.startMonoNs) / Units.NANOS_PER_MILLI,
                tzOffsetMin = h.tzOffsetMin,
                tzId = h.tzId,
                wakeKind = h.wakeKind,
                sourceDetail = h.sourceDetail,
                nominalImuHz = h.nominalImuHz,
                parking = parking,
            ),
            fixes = capture.fixes.filter { it.tMonoNs >= resumeNs },
            motion = capture.motion.filter { it.tMonoNs >= resumeNs },
            activity = capture.activity.filter { it.tMonoNs >= resumeNs },
            recordedAccel = emptyList(),
            marks = capture.marks.filter { it.tMonoNs >= resumeNs },
        )
        return SplitCapture(first, cutNs, second)
    }
}

/**
 * The capture as R0 append batches of at most [windowNs] each, in time order, for rewriting a split trip's log. The
 * recorded-accelerometer history, which precedes `t = 0`, goes in the first batch.
 */
public fun CapturedTrip.batches(windowNs: Long = DEFAULT_BATCH_NS): List<CaptureBatch> {
    val starts = listOfNotNull(
        fixes.firstOrNull()?.tMonoNs, motion.firstOrNull()?.tMonoNs, activity.firstOrNull()?.tMonoNs,
        marks.firstOrNull()?.tMonoNs,
    )
    val from = starts.minOrNull() ?: header.startMonoNs
    fun <T> bucket(items: List<T>, t: (T) -> Long): Map<Long, List<T>> = items.groupBy { (t(it) - from).floorDiv(windowNs) }
    val f = bucket(fixes) { it.tMonoNs }
    val m = bucket(motion) { it.tMonoNs }
    val a = bucket(activity) { it.tMonoNs }
    val k = bucket(marks) { it.tMonoNs }
    val keys = (f.keys + m.keys + a.keys + k.keys).sorted().ifEmpty { listOf(0L) }
    return keys.mapIndexed { i, key ->
        CaptureBatch(
            fixes = f[key].orEmpty(),
            motion = m[key].orEmpty(),
            activity = a[key].orEmpty(),
            recordedAccel = if (i == 0) recordedAccel else emptyList(),
            marks = k[key].orEmpty(),
        )
    }
}

private const val DEFAULT_BATCH_NS = 60_000_000_000L
