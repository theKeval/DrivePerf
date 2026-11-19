package com.driveperf.domain.analysis

import com.driveperf.domain.trip.EventType
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GgCloudTest {
    private fun cloud(lat: DoubleArray, lon: DoubleArray) = GgCloud(Series.uniform(10.0, lat), Series.uniform(10.0, lon))

    @Test
    fun countsMomentsWithinAndByDominantAxis() {
        val c = cloud(
            doubleArrayOf(0.05, 0.3, 0.0, 0.0, Double.NaN),
            doubleArrayOf(0.0, 0.1, -0.4, 0.1, 0.2),
        )
        assertEquals(4, c.moments)
        assertEquals(50, c.withinPct)
        assertEquals(mapOf(EventType.BRAKE to 1, EventType.ACCEL to 1, EventType.CORNER to 2), c.momentsByType)
    }

    @Test
    fun theDominantAxisPicksTheFilterClassAndATieIsCornering() {
        assertEquals(EventType.CORNER, GgCloud.typeOf(0.2, 0.2))
        assertEquals(EventType.CORNER, GgCloud.typeOf(-0.3, 0.1))
        assertEquals(EventType.BRAKE, GgCloud.typeOf(0.1, -0.3))
        assertEquals(EventType.ACCEL, GgCloud.typeOf(0.0, 0.3))
    }

    @Test
    fun cellsAreDensityWeightedAndClampedToTheScale() {
        val c = cloud(doubleArrayOf(0.01, 0.01, 0.01, 2.0), doubleArrayOf(0.01, 0.01, 0.01, 0.0))
        assertEquals(2, c.cells.size)
        val dense = c.cells.maxBy { it.count }
        assertEquals(3, dense.count)
        assertEquals(1.0, dense.weight)
        val edge = c.cells.minBy { it.count }
        assertTrue(edge.latG < GgCloud.SCALE_G && edge.latG > GgCloud.SCALE_G - GgCloud.CELL_G)
        assertTrue(edge.weight < 1.0)
    }

    @Test
    fun theEnvelopeReachesTheFurthestPointAndFillsEmptySectors() {
        val c = cloud(doubleArrayOf(0.4, -0.2, 0.0), doubleArrayOf(0.0, 0.0, -0.3))
        val env = c.envelope
        assertEquals(GgCloud.SECTORS, env.size)
        val radii = env.map { hypot(it.latG, it.lonG) }
        assertEquals(0.4, radii.max(), 1e-9)
        assertTrue(radii.all { it > 0.0 })
    }

    @Test
    fun anEmptyCloudHasNoCellsNoOutlineAndNoCaption() {
        val c = cloud(doubleArrayOf(Double.NaN), doubleArrayOf(0.1))
        assertEquals(0, c.moments)
        assertTrue(c.cells.isEmpty())
        assertTrue(c.envelope.isEmpty())
        assertNull(c.withinPct)
    }

    @Test
    fun theDotFollowsTheCursorAndHidesInGaps() {
        val c = cloud(doubleArrayOf(0.0, 0.2, Double.NaN), doubleArrayOf(0.0, -0.2, 0.0))
        val dot = c.dotAt(0.05)!!
        assertEquals(0.1, dot.latG, 1e-12)
        assertEquals(-0.1, dot.lonG, 1e-12)
        assertNull(c.dotAt(0.15))
        assertNull(c.dotAt(9.0))
    }
}
