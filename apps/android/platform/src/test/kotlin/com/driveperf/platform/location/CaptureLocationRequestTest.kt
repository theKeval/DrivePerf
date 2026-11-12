package com.driveperf.platform.location

import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLocationRequestTest {
    @Test
    fun `batching delays delivery and keeps high accuracy fixes at 1 Hz`() {
        val request = captureLocationRequest(batched = true)
        assertTrue(request.isBatched)
        assertEquals(3_000L, request.maxUpdateDelayMillis)
        assertEquals(1_000L, request.intervalMillis)
        assertEquals(1_000L, request.minUpdateIntervalMillis)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.priority)
    }

    @Test
    fun `live delivery is unbatched at 1 Hz`() {
        val request = captureLocationRequest(batched = false)
        assertFalse(request.isBatched)
        assertEquals(1_000L, request.maxUpdateDelayMillis)
        assertEquals(1_000L, request.intervalMillis)
    }
}
