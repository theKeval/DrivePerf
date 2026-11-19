package com.driveperf.domain.replay

import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.Trip

/**
 * Builds a [Replay] from what the trip already stores (DRIVE-200, DRIVE-61). Capture and scoring are untouched.
 *
 * Overlay spans, by priority where they overlap:
 * 1. [SegmentStyle.ESTIMATED]: the trip's `ESTIMATED` quality span (§2.8), drawn from the parking spot to the first fix.
 * 2. [SegmentStyle.NO_GPS]: the trip's `GAP` and `INTERRUPTED` spans (§2.7: every gap over 3 s while moving, tunnels,
 *    app kills), plus any wait between fixes longer than [NO_GPS_GAP_S] that the pipeline did not mark.
 * 3. [SegmentStyle.EXCLUDED]: the trip's `EXCLUDED` spans, with their reason.
 */
internal object ReplayBuilder {
    /**
     * The longest wait between fixes still drawn as a line when the pipeline did not mark it. The pipeline already
     * turns every gap over 3 s while moving (§2.3 continuity) into a `GAP` span, so this only catches waits it skips:
     * while the car stands still, before the first fix of a trip with no estimated start, after the last fix, and on
     * older trips. 10 s is longer than the fix dropouts phones show at a standstill, where the marker would not move
     * anyway, and short enough that a real outage is never bridged by a straight line.
     */
    const val NO_GPS_GAP_S: Double = 10.0

    /**
     * On the ≤ 400-row preview track a row stands for several fixes, so a wait only counts as a gap when it is this
     * many times the track's usual row spacing (and at least [NO_GPS_GAP_S]).
     */
    private const val SUMMARY_GAP_FACTOR = 2.5

    /**
     * @param fixes positioned fixes in time order; [parking] is the estimated start's parking spot, or null.
     */
    fun build(trip: Trip, parking: ReplayPoint?, fixes: List<ReplayPoint>, summaryOnly: Boolean): Replay? {
        val sorted = fixes.sortedBy { it.tS }
        val estimatedEnd = trip.quality?.spans.orEmpty().filter { it.kind == SegmentKind.ESTIMATED }.maxOfOrNull { it.t1S }
        val start = parking?.takeIf { estimatedEnd != null && (sorted.isEmpty() || it.tS < sorted.first().tS) }
        val track = listOfNotNull(start) + sorted
        if (track.isEmpty()) return null
        val duration = maxOf(trip.durationS ?: 0.0, track.last().tS)
        val gapS = if (summaryOnly) maxOf(NO_GPS_GAP_S, SUMMARY_GAP_FACTOR * medianSpacing(sorted)) else NO_GPS_GAP_S
        val raw = qualitySpans(trip, duration) + fixGaps(sorted, estimatedEnd ?: 0.0, duration, gapS)
        return Replay(trip.id, bounds(trip.bbox, track), duration, resolve(raw, duration), summaryOnly, track)
    }

    private fun qualitySpans(trip: Trip, duration: Double): List<ReplaySpan> =
        trip.quality?.spans.orEmpty().mapNotNull { s ->
            val style = when (s.kind) {
                SegmentKind.ESTIMATED -> SegmentStyle.ESTIMATED
                SegmentKind.GAP, SegmentKind.INTERRUPTED -> SegmentStyle.NO_GPS
                SegmentKind.EXCLUDED -> SegmentStyle.EXCLUDED
                SegmentKind.PAUSED -> null // no fixes while paused; the wait rule below marks it when it is long
            }
            val reason = s.reason.takeIf { style == SegmentStyle.EXCLUDED }
            style?.let { ReplaySpan(it, s.t0S.coerceIn(0.0, duration), s.t1S.coerceIn(0.0, duration), reason) }
        }

    /** Waits longer than [gapS] between fixes, before the first fix and after the last. */
    private fun fixGaps(fixes: List<ReplayPoint>, from: Double, duration: Double, gapS: Double): List<ReplaySpan> {
        if (fixes.isEmpty()) return listOf(ReplaySpan(SegmentStyle.NO_GPS, from, duration))
        val times = listOf(from) + fixes.map { it.tS } + duration
        return times.zipWithNext().filter { (a, b) -> b - a > gapS }.map { (a, b) -> ReplaySpan(SegmentStyle.NO_GPS, a, b) }
    }

    /** Cuts overlapping spans into sorted, non-overlapping ones; higher-priority styles win, and equal neighbours merge. */
    fun resolve(raw: List<ReplaySpan>, duration: Double): List<ReplaySpan> {
        val spans = raw.filter { it.t1S > it.t0S }
        val cuts = (spans.flatMap { listOf(it.t0S, it.t1S) } + 0.0 + duration).filter { it in 0.0..duration }.distinct().sorted()
        val out = mutableListOf<ReplaySpan>()
        for ((a, b) in cuts.zipWithNext()) {
            val mid = (a + b) / 2
            val top = spans.filter { mid >= it.t0S && mid < it.t1S }.minByOrNull { priority(it.style) } ?: continue
            val prev = out.lastOrNull()
            if (prev != null && prev.continuesInto(top, a)) {
                out[out.lastIndex] = prev.copy(t1S = b)
            } else {
                out += ReplaySpan(top.style, a, b, top.reason)
            }
        }
        return out
    }

    private fun ReplaySpan.continuesInto(next: ReplaySpan, at: Double): Boolean =
        t1S == at && style == next.style && reason == next.reason

    private fun priority(style: SegmentStyle): Int = when (style) {
        SegmentStyle.ESTIMATED -> 0
        SegmentStyle.NO_GPS -> 1
        SegmentStyle.EXCLUDED -> 2
        SegmentStyle.OBSERVED -> 3
    }

    private fun medianSpacing(fixes: List<ReplayPoint>): Double {
        val steps = fixes.zipWithNext { a, b -> b.tS - a.tS }.sorted()
        return if (steps.isEmpty()) 0.0 else steps[steps.size / 2]
    }

    private fun bounds(stored: BoundingBox?, track: List<ReplayPoint>): BoundingBox {
        val lats = track.map { it.lat } + listOfNotNull(stored?.minLat, stored?.maxLat)
        val lngs = track.map { it.lng } + listOfNotNull(stored?.minLng, stored?.maxLng)
        return BoundingBox(lats.min(), lngs.min(), lats.max(), lngs.max())
    }
}
