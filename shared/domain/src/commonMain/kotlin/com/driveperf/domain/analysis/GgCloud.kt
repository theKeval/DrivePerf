package com.driveperf.domain.analysis

import com.driveperf.domain.trip.EventType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A point of the friction circle: lateral G across (right positive), longitudinal G up (accelerating positive). */
public data class GgPoint(val latG: Double, val lonG: Double)

/**
 * One cell of the density cloud: the centre of a [GgCloud.CELL_G] square, how many moments fell in it, and [weight] in
 * `(0, 1]` for the dot's opacity. [type] is the cell's dominant axis, which the event-type filter emphasises.
 */
public data class GgCell(val latG: Double, val lonG: Double, val count: Int, val weight: Double, val type: EventType)

/**
 * The G-G plot of one trip (F-RPL-06): scorable moments only, at 10 Hz. Excluded, estimated and gap samples never reach
 * it (AC3, EC-MOV), so it shows only what was measured fairly.
 */
public class GgCloud(private val lat: Series, private val lon: Series) {
    init {
        require(lat.size == lon.size)
    }

    /** How many moments are plotted. */
    public val moments: Int = (0 until lat.size).count { !lat.values[it].isNaN() && !lon.values[it].isNaN() }

    /** The density cloud, within ±[SCALE_G] (moments beyond it sit on the edge cells). */
    public val cells: List<GgCell> by lazy { buildCells() }

    /** The furthest the drive went in each of [SECTORS] directions, as a closed outline (empty with no moments). */
    public val envelope: List<GgPoint> by lazy { buildEnvelope() }

    /** Moments of each class (the filter chips' "showing cornering, 31 moments"). */
    public val momentsByType: Map<EventType, Int> by lazy {
        val counts = IntArray(EventType.entries.size)
        forEachPoint { la, lo -> counts[typeOf(la, lo).ordinal]++ }
        EventType.entries.associateWith { counts[it.ordinal] }
    }

    /** The share of moments within 0.2 g, as a whole percentage; null with no moments. */
    public val withinPct: Int? by lazy {
        if (moments == 0) {
            null
        } else {
            var inside = 0
            forEachPoint { la, lo -> if (hypot(la, lo) <= WITHIN_G) inside++ }
            (inside * PERCENT / moments).roundToInt()
        }
    }

    /** The dot at the cursor, or null where nothing scorable was measured. */
    public fun dotAt(tS: Double): GgPoint? {
        val la = lat.valueAt(tS)
        val lo = lon.valueAt(tS)
        return if (la.isNaN() || lo.isNaN()) null else GgPoint(la, lo)
    }

    private inline fun forEachPoint(action: (Double, Double) -> Unit) {
        for (i in 0 until lat.size) {
            val la = lat.values[i]
            val lo = lon.values[i]
            if (!la.isNaN() && !lo.isNaN()) action(la, lo)
        }
    }

    private fun buildCells(): List<GgCell> {
        val side = (2 * SCALE_G / CELL_G).roundToInt()
        val grid = IntArray(side * side)
        forEachPoint { la, lo -> grid[cellIndex(lo, side) * side + cellIndex(la, side)]++ }
        val most = grid.maxOrNull() ?: 0
        if (most == 0) return emptyList()
        return grid.indices.filter { grid[it] > 0 }.map { i ->
            val la = -SCALE_G + (i % side + HALF) * CELL_G
            val lo = -SCALE_G + (i / side + HALF) * CELL_G
            GgCell(la, lo, grid[i], sqrt(grid[i].toDouble() / most), typeOf(la, lo))
        }
    }

    private fun cellIndex(g: Double, side: Int): Int = floor((g + SCALE_G) / CELL_G).toInt().coerceIn(0, side - 1)

    private fun buildEnvelope(): List<GgPoint> {
        val radius = DoubleArray(SECTORS) { Double.NaN }
        forEachPoint { la, lo ->
            val s = sectorOf(la, lo)
            val r = hypot(la, lo)
            if (radius[s].isNaN() || r > radius[s]) radius[s] = r
        }
        if (radius.all { it.isNaN() }) return emptyList()
        fillEmpty(radius)
        return (0 until SECTORS).map { s ->
            val a = (s + HALF) * 2 * PI / SECTORS - PI
            GgPoint(radius[s] * kotlin.math.cos(a), radius[s] * kotlin.math.sin(a))
        }
    }

    /** A sector nobody reached takes the mean of its nearest reached neighbours, so the outline has no spikes. */
    private fun fillEmpty(radius: DoubleArray) {
        val known = radius.copyOf()
        for (s in 0 until SECTORS) {
            if (!known[s].isNaN()) continue
            radius[s] = (nearest(known, s, 1) + nearest(known, s, -1)) / 2
        }
    }

    private fun nearest(r: DoubleArray, from: Int, step: Int): Double {
        var i = from
        repeat(SECTORS) {
            i = (i + step + SECTORS) % SECTORS
            if (!r[i].isNaN()) return r[i]
        }
        return 0.0
    }

    private fun sectorOf(la: Double, lo: Double): Int =
        floor((atan2(lo, la) + PI) / (2 * PI) * SECTORS).toInt().coerceIn(0, SECTORS - 1)

    public companion object {
        /** The fixed plot scale, so drives stay comparable (S-ANL-02 "How to read it"). */
        public const val SCALE_G: Double = 0.6

        /** Density cell size. */
        public const val CELL_G: Double = 0.02

        /** "{n}% of your inputs stayed within 0.2 g". */
        public const val WITHIN_G: Double = 0.2

        /** Outline resolution: 10° sectors. */
        public const val SECTORS: Int = 36

        private const val HALF = 0.5
        private const val PERCENT = 100.0

        /**
         * The filter class of a moment: its dominant axis (S-ANL-02 data note). Turning wins a tie, so a moment on the
         * diagonal counts as cornering.
         */
        public fun typeOf(latG: Double, lonG: Double): EventType = when {
            abs(latG) >= abs(lonG) -> EventType.CORNER
            lonG < 0 -> EventType.BRAKE
            else -> EventType.ACCEL
        }
    }
}
