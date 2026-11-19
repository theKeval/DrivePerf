package com.driveperf.feature.replay

import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.trip.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DRIVE-34: a tap on an event tick opens that event; a tap elsewhere on the scrubber still seeks. */
class ScrubberTicksTest {
    // One braking event peaking at 165 s of 300 s: x = 165 on a 300 px scrubber.
    private val events = listOf(EventMarker("e1", EventType.BRAKE, 160.0, 165.0, 170.0, 0.0, 0.0, needed = false, pinned = true))

    private fun tap(x: Float, y: Float, duration: Double = 300.0) = tickAt(x, y, 300, 48, events, duration, reachPx = 24f)

    @Test
    fun aTapOnTheTickLaneWithinReachOpensTheEvent() {
        assertEquals("e1", tap(145f, 10f))
        assertEquals("e1", tap(189f, 0f))
    }

    @Test
    fun aTapOnTheTrackOrOutOfReachSeeks() {
        assertNull(tap(140f, 10f))
        assertNull(tap(165f, 30f))
        assertNull(tap(165f, 10f, duration = 0.0))
        assertNull(tickAt(165f, 10f, 300, 48, emptyList(), 300.0, 24f))
    }
}
