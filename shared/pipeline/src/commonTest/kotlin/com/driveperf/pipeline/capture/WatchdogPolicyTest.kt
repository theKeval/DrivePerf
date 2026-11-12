package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.WatchdogState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-CAP-14 AC1: 24 h after the last check-in, at most once per 72 h. */
class WatchdogPolicyTest {
    private val policy = WatchdogPolicy(CaptureConfig())
    private val hour = 3_600_000L
    private val t0 = 1_794_268_800_000L

    @Test
    fun eachCheckInMovesTheNotification24HoursAhead() {
        val first = policy.checkIn(WatchdogState(), t0)
        assertEquals(WatchdogState(t0, t0 + 24 * hour, null), first.state)
        assertFalse(first.fired)
        val second = policy.checkIn(first.state, t0 + 5 * hour)
        assertEquals(t0 + 29 * hour, second.state.dueWallMs)
        assertFalse(second.fired)
    }

    @Test
    fun aDueNotificationCountsAsFiredAndHoldsTheNextOneFor72Hours() {
        val armed = policy.checkIn(WatchdogState(), t0).state
        val late = policy.checkIn(armed, t0 + 30 * hour)
        assertTrue(late.fired)
        assertEquals(t0 + 24 * hour, late.state.lastFiredWallMs)
        assertEquals(t0 + 24 * hour + 72 * hour, late.state.dueWallMs, "72 h after the last one, not 24 h after now")
        val muchLater = policy.checkIn(late.state, t0 + 200 * hour)
        assertTrue(muchLater.fired)
        assertEquals(t0 + 224 * hour, muchLater.state.dueWallMs, "past the 72 h gap, 24 h silence applies again")
    }

    @Test
    fun turningDetectionOffClearsTheDueTimeOnly() {
        val armed = WatchdogState(t0, t0 + 24 * hour, t0 - hour)
        val off = policy.off(armed)
        assertNull(off.dueWallMs)
        assertEquals(t0, off.lastCheckInWallMs)
        assertFalse(policy.checkIn(off, t0 + 100 * hour).fired, "nothing was scheduled while off")
    }
}
