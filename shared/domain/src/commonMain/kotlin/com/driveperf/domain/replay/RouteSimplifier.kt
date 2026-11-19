package com.driveperf.domain.replay

/**
 * Douglas–Peucker for every tolerance at once (ADR-005: simplified polylines per zoom bucket).
 *
 * Each interior point gets the tolerance below which classic Douglas–Peucker keeps it: its distance from the chord at
 * the split that picked it, capped by the value of the split above it. Keeping the points whose importance exceeds a
 * tolerance then gives exactly the Douglas–Peucker result for that tolerance, so one pass serves every zoom. The end
 * points of every run get infinity, so run boundaries (style changes) always survive.
 */
internal object RouteSimplifier {
    fun importance(track: List<ReplayPoint>, runs: List<IntRange>): DoubleArray {
        val out = DoubleArray(track.size)
        for (run in runs) {
            out[run.first] = Double.POSITIVE_INFINITY
            out[run.last] = Double.POSITIVE_INFINITY
            split(track, run.first, run.last, out)
        }
        return out
    }

    /** Iterative, so a long straight run cannot overflow the stack. */
    private fun split(track: List<ReplayPoint>, first: Int, last: Int, out: DoubleArray) {
        val stack = ArrayDeque<Triple<Int, Int, Double>>()
        stack.addLast(Triple(first, last, Double.POSITIVE_INFINITY))
        while (stack.isNotEmpty()) {
            val (a, b, cap) = stack.removeLast()
            if (b - a < 2) continue
            var best = a + 1
            var bestD = -1.0
            for (k in a + 1 until b) {
                val d = RouteGeo.crossTrackM(track[k], track[a], track[b])
                if (d > bestD) {
                    bestD = d
                    best = k
                }
            }
            val value = minOf(bestD, cap)
            out[best] = value
            stack.addLast(Triple(a, best, value))
            stack.addLast(Triple(best, b, value))
        }
    }
}
