package com.driveperf.presentation.score

import com.driveperf.domain.trip.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TipCopyTest {
    @Test
    fun `each event type links to the first tip of its category`() {
        assertEquals("brake_light", TipCopy.relatedTipId(EventType.BRAKE))
        assertEquals("accel_progressive", TipCopy.relatedTipId(EventType.ACCEL))
        assertEquals("corner_brake_before", TipCopy.relatedTipId(EventType.CORNER))
    }

    @Test
    fun `the stub has no copy yet`() {
        assertNull(TipCopy.text("brake_light"))
    }
}
