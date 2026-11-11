package com.driveperf.platform.capture

import com.driveperf.platform.Fixtures.fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovingGapTrackerTest {
    @Test
    fun `a trip that never moved has no delivery figure`() {
        val tracker = MovingGapTracker()
        listOf(0.0, 1.0, 2.0).forEach { tracker.onFix(fix(it, speedMps = 0.0)) }
        assertNull(tracker.deliveryPercent)
    }

    @Test
    fun `complete delivery while moving is 100 percent`() {
        val tracker = MovingGapTracker()
        (0..60).forEach { tracker.onFix(fix(it.toDouble(), speedMps = 12.0)) }
        assertEquals(100, tracker.deliveryPercent)
        assertEquals(60.0, tracker.movingSeconds, 1e-9)
    }

    @Test
    fun `a long wait after parking is not counted as a gap`() {
        // Spike 1, trip 5: a 77 s drive then 4 stationary minutes with a fix every 6 s was 35.8% overall but 100% moving.
        val tracker = MovingGapTracker()
        (0..77).forEach { tracker.onFix(fix(it.toDouble(), speedMps = 12.0)) }
        tracker.onFix(fix(78.0, speedMps = 0.0))
        var t = 84.0
        while (t < 320.0) {
            tracker.onFix(fix(t, speedMps = 0.0))
            t += 6.0
        }
        assertEquals(100, tracker.deliveryPercent)
    }

    @Test
    fun `a gap while moving lowers delivery`() {
        val tracker = MovingGapTracker()
        (0..10).forEach { tracker.onFix(fix(it.toDouble(), speedMps = 15.0)) }
        tracker.onFix(fix(20.0, speedMps = 15.0)) // 10 s with no fix at speed: a tunnel
        assertEquals(10.0, tracker.gapSeconds, 1e-9)
        assertEquals(20.0, tracker.movingSeconds, 1e-9)
        assertEquals(50, tracker.deliveryPercent)
    }

    @Test
    fun `a gap of exactly three seconds is still continuous`() {
        val tracker = MovingGapTracker()
        tracker.onFix(fix(0.0, speedMps = 15.0))
        tracker.onFix(fix(3.0, speedMps = 15.0))
        assertEquals(0.0, tracker.gapSeconds, 1e-9)
    }

    @Test
    fun `an out of order fix is ignored`() {
        val tracker = MovingGapTracker()
        tracker.onFix(fix(5.0, speedMps = 15.0))
        tracker.onFix(fix(4.0, speedMps = 15.0))
        assertEquals(0.0, tracker.movingSeconds, 1e-9)
    }
}
