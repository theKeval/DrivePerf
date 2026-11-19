package com.driveperf.domain.replay

import com.driveperf.domain.replay.ReplayFixtures.STEP_DEG
import com.driveperf.domain.replay.ReplayFixtures.span
import com.driveperf.domain.replay.ReplayFixtures.trip
import com.driveperf.domain.trip.SegmentKind
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouteSimplifierTest {
    /** A wiggly synthetic road: a sine around a northbound line, with jitter. */
    private fun wiggly(n: Int, seed: Int = 7): List<ReplayPoint> {
        val r = Random(seed)
        return (0 until n).map {
            val lng = sin(it / 15.0) * 20 * STEP_DEG + (r.nextDouble() - 0.5) * STEP_DEG
            ReplayPoint(it.toDouble(), it * STEP_DEG, lng, 11.0)
        }
    }

    /** Textbook recursive Douglas–Peucker, as the reference. */
    private fun classic(points: List<ReplayPoint>, toleranceM: Double): List<ReplayPoint> {
        if (points.size < 3) return points
        val a = points.first()
        val b = points.last()
        val (k, d) = (1 until points.lastIndex).map { it to RouteGeo.crossTrackM(points[it], a, b) }.maxBy { it.second }
        return if (d <= toleranceM) {
            listOf(a, b)
        } else {
            classic(points.subList(0, k + 1), toleranceM).dropLast(1) + classic(points.subList(k, points.size), toleranceM)
        }
    }

    @Test
    fun oneImportancePassMatchesDouglasPeuckerAtEveryTolerance() {
        val track = wiggly(500)
        val importance = RouteSimplifier.importance(track, listOf(track.indices))
        for (tol in listOf(0.1, 1.0, 5.0, 20.0, 100.0, 1_000.0)) {
            val kept = track.indices.filter { importance[it] > tol }.map { track[it] }
            assertEquals(classic(track, tol), kept, "tolerance $tol m")
        }
    }

    @Test
    fun simplificationKeepsStyleBoundariesAtEveryZoom() {
        val track = wiggly(600)
        val replay = assertNotNull(
            ReplayBuilder.build(
                trip(listOf(span(SegmentKind.EXCLUDED, 101.5, 203.5), span(SegmentKind.GAP, 400.0, 420.0)), 599.0),
                null,
                track.filterNot { it.tS > 400 && it.tS < 420 },
                summaryOnly = false,
            ),
        )
        val full = replay.segments(Replay.MAX_ZOOM)
        val boundaries = full.map { it.points.first() } + full.last().points.last()
        for (zoom in 0..Replay.MAX_ZOOM + 2) {
            val segments = replay.segments(zoom)
            assertEquals(full.map { it.style }, segments.map { it.style }, "zoom $zoom")
            assertEquals(boundaries, segments.map { it.points.first() } + segments.last().points.last(), "zoom $zoom")
            segments.zipWithNext().forEach { (a, b) -> assertEquals(a.points.last(), b.points.first()) }
        }
        val far = replay.segments(0).sumOf { it.points.size }
        val near = replay.segments(Replay.MAX_ZOOM).sumOf { it.points.size }
        assertTrue(far < 20, "zoomed out the route collapses to its boundaries: $far points")
        assertTrue(near > 500, "zoomed in nearly every fix stays: $near points")
        assertTrue(replay.segments(-3) == replay.segments(0))
    }

    @Test
    fun aLongStraightRunDoesNotOverflow() {
        val track = (0 until 20_000).map { ReplayPoint(it.toDouble(), it * 1e-6, 0.0, 1.0) }
        val importance = RouteSimplifier.importance(track, listOf(track.indices))
        assertEquals(2, importance.count { it > 0.01 })
    }
}
