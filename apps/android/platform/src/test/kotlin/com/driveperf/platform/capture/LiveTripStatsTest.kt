package com.driveperf.platform.capture

import com.driveperf.platform.Fixtures.fix
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTripStatsTest {
    // 0.001 degrees of longitude on the equator is about 111.3 m.
    private val stepDeg = 0.001
    private val stepM = 111.19

    @Test
    fun `distance adds up between accurate fixes`() {
        val stats = LiveTripStats()
        (0..3).forEach { stats.onFix(fix(it.toDouble(), lng = it * stepDeg)) }
        assertEquals(3 * stepM, stats.distanceM, 1.0)
        assertEquals(4, stats.fixCount)
    }

    @Test
    fun `fixes with poor accuracy are counted but add no distance`() {
        val stats = LiveTripStats()
        stats.onFix(fix(0.0, lng = 0.0))
        stats.onFix(fix(1.0, lng = stepDeg, hAccM = 80.0))
        stats.onFix(fix(2.0, lng = stepDeg, hAccM = null))
        assertEquals(0.0, stats.distanceM, 1e-9)
        assertEquals(3, stats.fixCount)
    }

    @Test
    fun `jitter under five metres is dropped`() {
        val stats = LiveTripStats()
        stats.onFix(fix(0.0, lng = 0.0))
        stats.onFix(fix(1.0, lng = 0.00002)) // about 2 m
        assertEquals(0.0, stats.distanceM, 1e-9)
    }

    @Test
    fun `slow movement adds up even when every step is under five metres`() {
        val stats = LiveTripStats()
        // 10 km/h at one fix a second is about 2.8 m a fix: each step alone is jitter, together they are a drive.
        (0..20).forEach { stats.onFix(fix(it.toDouble(), lng = it * 0.000025)) }
        assertEquals(20 * 0.000025 * 111_190, stats.distanceM, 6.0)
    }
}
