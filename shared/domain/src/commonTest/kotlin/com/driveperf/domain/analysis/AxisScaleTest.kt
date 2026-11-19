package com.driveperf.domain.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxisScaleTest {
    @Test
    fun timeAxisIsTheIdentityClamped() {
        val axis = TimeAxis(100.0)
        assertEquals(100.0, axis.length)
        assertEquals(40.0, axis.xOf(40.0))
        assertEquals(0.0, axis.xOf(-5.0))
        assertEquals(100.0, axis.tOf(120.0))
    }

    @Test
    fun distanceAxisMapsBothWaysAndStandstillsReadTheirFirstMoment() {
        val axis = DistanceAxis(doubleArrayOf(10.0, 20.0, 30.0, 40.0), doubleArrayOf(0.0, 100.0, 100.0, 300.0), 50.0)
        assertEquals(300.0, axis.length)
        assertEquals(0.0, axis.xOf(5.0))
        assertEquals(50.0, axis.xOf(15.0))
        assertEquals(100.0, axis.xOf(25.0))
        assertEquals(300.0, axis.xOf(45.0))
        assertEquals(0.0, axis.tOf(0.0))
        assertEquals(15.0, axis.tOf(50.0))
        assertEquals(20.0, axis.tOf(100.0))
        assertEquals(35.0, axis.tOf(200.0))
        assertEquals(50.0, axis.tOf(301.0))
    }

    @Test
    fun anEmptyDistanceAxisIsZero() {
        val axis = DistanceAxis(DoubleArray(0), DoubleArray(0), 10.0)
        assertEquals(0.0, axis.length)
        assertEquals(0.0, axis.xOf(3.0))
        assertEquals(0.0, axis.tOf(3.0))
    }

    @Test
    fun laneRangeNormalises() {
        assertEquals(0.5, LaneRange(-0.6, 0.6).norm(0.0))
        assertEquals(1.0, LaneRange(0.0, 10.0).norm(10.0))
        assertEquals(0.5, LaneRange(3.0, 3.0).norm(9.0))
        assertTrue(LaneRange(0.0, 1.0).norm(Double.NaN).isNaN())
    }

    @Test
    fun aShortWindowDrawsEverySampleWithItsGaps() {
        val track = ChannelTrack(Series.uniform(1.0, doubleArrayOf(0.0, 1.0, Double.NaN, 3.0, 4.0)))
        val path = track.path(TimeAxis(4.0), 0.0, 4.0, 10, LaneRange(0.0, 4.0))
        assertEquals(5, path.size)
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), path.xs.toList())
        assertEquals(0.25, path.ys[1])
        assertTrue(path.ys[2].isNaN())
    }

    @Test
    fun aLongWindowIsDownsampledToMinMaxColumnsWithBreaks() {
        val values = DoubleArray(10_000) { if (it in 5_000 until 5_100) Double.NaN else (it % 10).toDouble() }
        val track = ChannelTrack(Series.uniform(50.0, values))
        val path = track.path(TimeAxis(200.0), 0.0, 200.0, 100, LaneRange(0.0, 9.0))
        assertTrue(path.size <= 300)
        assertEquals(0.0, path.ys[0])
        assertEquals(1.0, path.ys[1])
        assertTrue(path.ys.any { it.isNaN() })
        assertEquals(0.005, path.xs[0], 1e-12)
    }

    @Test
    fun zoomedWindowsCostTheSameAndEmptyInputsDrawNothing() {
        val track = ChannelTrack(Series.uniform(50.0, DoubleArray(180_000) { it.toDouble() }))
        val axis = TimeAxis(3600.0)
        val zoomed = track.path(axis, 1800.0, 2025.0, 400, LaneRange(0.0, 180_000.0))
        assertTrue(zoomed.size <= 1200)
        assertTrue(zoomed.xs.first() >= 0.0 && zoomed.xs.last() <= 1.0)
        assertEquals(0, track.path(axis, 10.0, 10.0, 400, LaneRange(0.0, 1.0)).size)
        assertEquals(0, track.path(axis, 0.0, 10.0, 0, LaneRange(0.0, 1.0)).size)
        assertEquals(0, ChannelTrack(Series.uniform(1.0, DoubleArray(0))).path(axis, 0.0, 10.0, 4, LaneRange(0.0, 1.0)).size)
    }

    @Test
    fun theDistanceAxisSkipsColumnsWithNoSampleWithoutBreaking() {
        val times = DoubleArray(2_000) { it.toDouble() }
        val speed = ChannelTrack(Series.sampled(times, DoubleArray(2_000) { 10.0 }))
        val axis = DistanceAxis(doubleArrayOf(0.0, 1_800.0, 1_999.0), doubleArrayOf(0.0, 1_000.0, 100_000.0), 1_999.0)
        val path = speed.path(axis, 0.0, 100_000.0, 600, LaneRange(0.0, 20.0))
        assertTrue(path.ys.none { it.isNaN() })
        assertTrue(path.size < 2 * 600)
    }
}
