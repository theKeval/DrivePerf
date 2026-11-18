package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.Vec3
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.motion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CruiseRuleTest {
    private val rule = CruiseRule(CaptureConfig())

    private fun steadyCruise(): Cruise =
        assertNotNull((0..60).fold(null as Cruise?) { c, i -> rule.next(c, fix(i.toDouble(), 20.0)) })

    @Test
    fun singleSampleVibrationAndVerticalShocksStayInCruise() {
        val samples = List(25) { i ->
            val t = 61.0 + i / 25.0
            when (i) {
                4, 16 -> motion(t, accel = 0.12)
                8 -> motion(t).copy(userAccelG = Vec3(0.0, 0.0, 0.75))
                else -> motion(t)
            }
        }
        assertTrue(assertNotNull(rule.afterMotion(steadyCruise(), samples)).steady)
    }

    @Test
    fun sustainedHorizontalBrakeExitsAt25Hz() {
        var cruise: Cruise? = steadyCruise()
        val start = 61.0
        val brake = List(25) { motion(start + it / 25.0, accel = -0.15) }
        var exitS: Double? = null
        for (sample in brake) {
            cruise = rule.afterMotion(cruise, listOf(sample))
            if (cruise == null) {
                exitS = sample.tMonoNs / 1e9 - start
                break
            }
        }
        assertNull(cruise)
        assertTrue(assertNotNull(exitS) <= 0.25)
    }

    @Test
    fun theFirstSideOfALaneChangeCanExitCruise() {
        val samples = List(8) { i ->
            motion(61.0 + i / 25.0).copy(userAccelG = Vec3(0.0, 0.15, 0.0))
        }
        assertNull(rule.afterMotion(steadyCruise(), samples))
    }

    @Test
    fun aSpeedBandChangeKeepsRecentMatchingFixes() {
        var cruise = rule.next(null, fix(0.0, 18.0))
        for (i in 1..45) cruise = rule.next(cruise, fix(i.toDouble(), 20.0))
        cruise = rule.next(cruise, fix(46.0, 21.0))
        for (i in 47..60) cruise = rule.next(cruise, fix(i.toDouble(), 21.0))
        assertFalse(assertNotNull(cruise).steady)
        cruise = rule.next(cruise, fix(61.0, 21.0))
        assertTrue(assertNotNull(cruise).steady)
        assertEquals(fix(1.0, 20.0).tMonoNs, cruise.fixes.first().tMonoNs)
    }
}
