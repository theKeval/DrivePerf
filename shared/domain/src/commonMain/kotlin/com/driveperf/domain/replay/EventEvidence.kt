package com.driveperf.domain.replay

import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisTrace
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** One motion sample of an evidence window: longitudinal and lateral G in the vehicle frame, at [tS] trip seconds. */
public data class EvidenceSample(val tS: Double, val lonG: Double, val latG: Double)

/** One gated GNSS speed of an evidence window, at [tS] trip seconds. */
public data class EvidenceSpeed(val tS: Double, val speedMps: Double)

/**
 * The evidence for one event (F-RPL-04 AC1): speed and G from [t0S] to [t1S] (by default 10 s either side of the
 * event), with the event's start, peak and end. Times are trip seconds; charts put [tPeakS] at 0.
 */
public data class EventEvidence(
    val eventId: String,
    val t0S: Double,
    val t1S: Double,
    val tStartS: Double,
    val tPeakS: Double,
    val tEndS: Double,
    val g: List<EvidenceSample>,
    val speed: List<EvidenceSpeed>,
) {
    /** The largest |G| on either axis in the window, for the chart's symmetric range. */
    public val maxAbsG: Double get() = g.maxOfOrNull { maxOf(abs(it.lonG), abs(it.latG)) } ?: 0.0

    /** The fastest speed in the window, for the speed lane's range. */
    public val maxSpeedMps: Double get() = speed.maxOfOrNull { it.speedMps } ?: 0.0
}

/** Cuts evidence windows out of a trip's R1 file (pipeline-and-scoring.md §0.3: the trace is not stored per event). */
public object EvidenceWindows {
    /** Seconds either side of the event's peak (F-RPL-04 AC1; A12 may tune it). */
    public const val WINDOW_S: Double = 10.0

    /**
     * The window around [event]: `[tPeakS − windowS, tPeakS + windowS]`, clamped to the trace. Null when the window
     * holds no motion sample at all (an event the trace does not cover).
     */
    public fun cut(trace: AnalysisTrace, event: DrivingEvent, windowS: Double = WINDOW_S): EventEvidence? {
        val rate = trace.rateHz.toDouble()
        val t0 = maxOf(0.0, event.tPeakS - windowS)
        val t1 = event.tPeakS + windowS
        val first = if (rate > 0) ceil(t0 * rate).toInt() else 0
        val last = if (rate > 0) floor(t1 * rate).toInt().coerceAtMost(trace.size - 1) else -1
        if (first > last) return null
        val g = (first..last).map { k -> EvidenceSample(k / rate, trace.lonG[k], trace.latG[k]) }
        val speed = trace.fixes.filter { it.tS in t0..t1 }.map { EvidenceSpeed(it.tS, it.speedMps) }
        return EventEvidence(event.id, t0, t1, event.tStartS, event.tPeakS, event.tEndS, g, speed)
    }
}

/**
 * The evidence of one trip's events, read from its R1 file once and kept while the sheet steps from event to event.
 * Null for an event when the trace was not kept (EC-STO: "Detailed trace not kept") or does not cover it.
 */
public class TripEvidence(
    private val trips: TripRepository,
    private val analysis: AnalysisFileStore,
) {
    private var cachedTripId: String? = null
    private var cachedTrace: AnalysisTrace? = null

    public suspend fun evidence(tripId: String, event: DrivingEvent): EventEvidence? {
        val trace = trace(tripId) ?: return null
        return EvidenceWindows.cut(trace, event)
    }

    private suspend fun trace(tripId: String): AnalysisTrace? {
        if (cachedTripId == tripId) return cachedTrace
        val trip = trips.trip(tripId)
        val trace = if (trip == null || trip.rawState == RawState.SUMMARY_ONLY) null else analysis.read(tripId)
        cachedTripId = tripId
        cachedTrace = trace
        return trace
    }
}
