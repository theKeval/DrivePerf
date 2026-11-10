package com.driveperf.platform.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class GravityEstimatorTest {
    private val stepNs = 20_000_000L // 50 Hz

    @Test
    fun `settles on a constant gravity reading`() {
        val estimator = GravityEstimator()
        var g = doubleArrayOf()
        repeat(500) { g = estimator.update(it * stepNs, 0.0, 0.0, 9.81) }
        assertEquals(9.81, g[2], 1e-6)
    }

    @Test
    fun `a brief acceleration barely moves the gravity estimate`() {
        val estimator = GravityEstimator()
        repeat(500) { estimator.update(it * stepNs, 0.0, 0.0, 9.81) }
        // A 0.1 s jolt of 3 m/s² (a pothole): the 0.3 Hz low-pass must leave most of it in the linear acceleration.
        var g = doubleArrayOf()
        repeat(5) { g = estimator.update((500 + it) * stepNs, 3.0, 0.0, 9.81) }
        assertEquals(0.0, g[0], 0.6)
    }

    @Test
    fun `tracks a slow tilt`() {
        val estimator = GravityEstimator()
        repeat(100) { estimator.update(it * stepNs, 0.0, 0.0, 9.81) }
        var g = doubleArrayOf()
        repeat(1_000) { g = estimator.update((100 + it) * stepNs, 9.81, 0.0, 0.0) } // 20 s later
        assertEquals(9.81, g[0], 0.05)
    }

    @Test
    fun `magnitude is the length of the estimate`() {
        val estimator = GravityEstimator()
        estimator.update(0, 3.0, 4.0, 0.0)
        assertEquals(5.0, estimator.magnitude(), 1e-9)
    }
}
