package com.driveperf.pipeline.route

import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.TripSegment
import com.driveperf.pipeline.score.R1Fix
import kotlin.math.abs

/**
 * The preview track (data-model.md §2.5): at most 400 time-ordered rows from gated fixes, plus the parking spot as an
 * estimated first row when the start was estimated. `q`: 0 observed, 1 estimated, 2 gap, 3 excluded.
 */
internal object PreviewTrack {
    private const val MAX_ROWS = 400
    private const val OBSERVED = 0
    private const val ESTIMATED = 1
    private const val GAP = 2
    private const val EXCLUDED = 3

    @Suppress("LongParameterList")
    fun build(
        fixes: List<R1Fix>,
        lonG: DoubleArray,
        latG: DoubleArray,
        fs: Int,
        spans: List<TripSegment>,
        parking: Pair<Double, Double>?,
        eventPeaksS: List<Double>,
    ): List<PreviewRow> {
        val positioned = fixes.filter { it.lat != null && it.lng != null }
        val budget = MAX_ROWS - (if (parking != null) 1 else 0)
        val keep = sample(positioned, eventPeaksS, budget)
        val rows = keep.map { f ->
            val k = (f.tS * fs).toInt().coerceIn(0, maxOf(0, lonG.size - 1))
            PreviewRow(
                tS = f.tS,
                lat = f.lat!!,
                lng = f.lng!!,
                speedMps = f.speedMps,
                lonG = lonG.getOrNull(k),
                latG = latG.getOrNull(k),
                elevM = null,
                quality = qualityAt(f.tS, spans),
            )
        }
        val first = parking?.let { PreviewRow(0.0, it.first, it.second, null, null, null, null, ESTIMATED) }
        return listOfNotNull(first) + rows
    }

    /** Evenly spaced rows, always keeping the first, the last and the rows nearest event peaks. */
    private fun sample(fixes: List<R1Fix>, peaks: List<Double>, budget: Int): List<R1Fix> {
        if (fixes.size <= budget) return fixes
        val pinned = peaks.mapNotNull { p -> fixes.indices.minByOrNull { abs(fixes[it].tS - p) } }.toSet()
        val room = maxOf(2, budget - pinned.size)
        val step = (fixes.size - 1).toDouble() / (room - 1)
        val even = (0 until room).map { (it * step).toInt() }.toSet()
        return (even + pinned + fixes.lastIndex).sorted().take(budget).map { fixes[it] }
    }

    private fun qualityAt(t: Double, spans: List<TripSegment>): Int {
        val span = spans.firstOrNull { t >= it.t0S && t < it.t1S && it.kind != SegmentKind.PAUSED }
        return when (span?.kind) {
            null, SegmentKind.PAUSED -> OBSERVED
            SegmentKind.ESTIMATED -> ESTIMATED
            SegmentKind.GAP, SegmentKind.INTERRUPTED -> GAP
            SegmentKind.EXCLUDED -> EXCLUDED
        }
    }
}
