package com.driveperf.domain.analysis

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeriesTest {
    @Test
    fun uniformTimesAndLowerBound() {
        val s = Series.uniform(10.0, DoubleArray(5) { it.toDouble() }, t0S = 1.0)
        assertEquals(1.0, s.timeAt(0))
        assertEquals(1.4, s.timeAt(4), 1e-12)
        assertEquals(0, s.lowerBound(-3.0))
        assertEquals(2, s.lowerBound(1.15))
        assertEquals(2, s.lowerBound(1.2))
        assertEquals(5, s.lowerBound(9.0))
    }

    @Test
    fun sampledLowerBoundIsBinarySearch() {
        val s = Series.sampled(doubleArrayOf(0.0, 1.0, 5.0, 6.0), doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(0, s.lowerBound(-1.0))
        assertEquals(2, s.lowerBound(1.5))
        assertEquals(2, s.lowerBound(5.0))
        assertEquals(4, s.lowerBound(7.0))
    }

    @Test
    fun valueAtInterpolatesAndGapsReadAsNaN() {
        val s = Series.sampled(doubleArrayOf(0.0, 2.0, 3.0, 4.0), doubleArrayOf(0.0, 4.0, Double.NaN, 8.0))
        assertEquals(0.0, s.valueAt(0.0))
        assertEquals(2.0, s.valueAt(1.0))
        assertEquals(4.0, s.valueAt(2.0))
        assertTrue(s.valueAt(2.5).isNaN())
        assertTrue(s.valueAt(3.5).isNaN())
        assertEquals(8.0, s.valueAt(4.0))
        assertTrue(s.valueAt(-1.0).isNaN())
        assertTrue(s.valueAt(4.5).isNaN())
        assertTrue(Series.sampled(DoubleArray(0), DoubleArray(0)).valueAt(0.0).isNaN())
    }

    @Test
    fun minAndMaxSkipGaps() {
        val s = Series.uniform(1.0, doubleArrayOf(Double.NaN, 3.0, -2.0, 7.0, Double.NaN))
        assertEquals(7.0, s.maxValue())
        assertEquals(-2.0, s.minValue())
        assertTrue(Series.uniform(1.0, doubleArrayOf(Double.NaN)).maxValue().isNaN())
        assertTrue(Series.uniform(1.0, doubleArrayOf(Double.NaN)).minValue().isNaN())
    }

    @Test
    fun pyramidMatchesAScanForAnyRange() {
        val rnd = Random(7)
        val values = DoubleArray(5_003) { if (rnd.nextInt(97) == 0) Double.NaN else rnd.nextDouble(-1.0, 1.0) }
        val pyramid = MinMaxPyramid(values)
        val got = RangeStats()
        val want = RangeStats()
        repeat(400) {
            val a = rnd.nextInt(values.size)
            val b = a + rnd.nextInt(values.size - a + 1)
            pyramid.query(a, b, got)
            want.reset()
            for (i in a until b) want.add(values[i])
            assertEquals(want.count, got.count, "count $a..$b")
            assertEquals(want.gap, got.gap, "gap $a..$b")
            if (want.count > 0) {
                assertTrue(abs(want.min - got.min) < 1e-6, "min $a..$b")
                assertTrue(abs(want.max - got.max) < 1e-6, "max $a..$b")
            }
        }
    }

    @Test
    fun pyramidClampsAndHandlesTinyInputs() {
        val pyramid = MinMaxPyramid(doubleArrayOf(2.0, Double.NaN, 5.0))
        val out = RangeStats()
        pyramid.query(-4, 99, out)
        assertEquals(2, out.count)
        assertTrue(out.gap)
        assertEquals(2.0, out.min)
        assertEquals(5.0, out.max)
        pyramid.query(1, 2, out)
        assertEquals(0, out.count)
        assertTrue(out.gap)
        val wide = MinMaxPyramid(DoubleArray(64) { it.toDouble() })
        wide.query(0, 64, out)
        assertEquals(0.0, out.min)
        assertEquals(63.0, out.max)
        assertFalse(out.gap)
    }
}
