package com.driveperf.domain.capture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GravityTrackerTest {
    private val still = Vec3(0.0, 0.0, 0.0)

    @Test
    fun theFirstSampleIsAllGravity() {
        val split = GravityTracker().update(0, Vec3(0.0, 0.6, 0.8), still)
        assertEquals(Vec3(0.0, 0.6, 0.8), split.gravityUp)
        assertEquals(0.0, split.userAccelG.y, 1e-12)
    }

    @Test
    fun aRunningMeanSettlesATripThatStartsMidManoeuvre() {
        val t = GravityTracker(timeConstantS = 10.0)
        t.update(0, Vec3(0.3, 0.0, 1.0), still) // starts while braking
        var last = t.update(NS_20MS, Vec3(0.0, 0.0, 1.0), still)
        for (k in 2..250) last = t.update(k * NS_20MS, Vec3(0.0, 0.0, 1.0), still)
        assertEquals(0.0, last.userAccelG.x, 0.002)
    }

    @Test
    fun sustainedAccelerationIsNotAbsorbedIntoGravity() {
        val t = GravityTracker()
        var k = 0L
        repeat(STEPS_40S) { t.update(k++ * NS_20MS, Vec3(0.0, 0.0, 1.0), still) }
        var last = t.update(k++ * NS_20MS, Vec3(0.3, 0.0, 1.0), still)
        repeat(STEPS_5S) { last = t.update(k++ * NS_20MS, Vec3(0.3, 0.0, 1.0), still) }
        assertTrue(last.userAccelG.x > 0.25, "kept ${last.userAccelG.x} g of 0.3 g after 5 s")
    }

    @Test
    fun theGyroscopeCarriesGravityThroughATurnOfThePhone() {
        val t = GravityTracker()
        var k = 0L
        repeat(STEPS_40S) { t.update(k++ * NS_20MS, Vec3(0.0, 0.0, 1.0), still) }
        val rateDps = Vec3(90.0, 0.0, 0.0) // counter-clockwise about x: gravity swings from +z towards +y
        var last = t.update(k * NS_20MS, Vec3(0.0, 0.0, 1.0), rateDps)
        for (i in 1..STEPS_1S) {
            val a = PI / 2 * i / STEPS_1S
            last = t.update((k + i) * NS_20MS, Vec3(0.0, sin(a), cos(a)), rateDps)
        }
        assertEquals(1.0, last.gravityUp.y, 0.02)
        assertEquals(0.0, last.userAccelG.y, 0.03)
    }

    @Test
    fun aLongGapSkipsTheGyroscopeStep() {
        val t = GravityTracker()
        t.update(0, Vec3(0.0, 0.0, 1.0), still)
        val after = t.update(5_000_000_000L, Vec3(0.0, 0.0, 1.0), Vec3(500.0, 0.0, 0.0))
        assertEquals(1.0, after.gravityUp.z, 1e-9)
    }

    @Test
    fun aZeroReadingHasNoDirection() {
        assertEquals(Vec3(0.0, 0.0, 0.0), GravityTracker().update(0, Vec3(0.0, 0.0, 0.0), still).gravityUp)
    }

    private companion object {
        const val NS_20MS = 20_000_000L
        const val STEPS_1S = 50
        const val STEPS_5S = 250
        const val STEPS_40S = 2000
    }
}
