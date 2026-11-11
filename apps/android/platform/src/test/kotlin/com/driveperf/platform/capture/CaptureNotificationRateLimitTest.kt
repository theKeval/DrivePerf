package com.driveperf.platform.capture

import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureNotificationRateLimitTest {
    private val limit = CaptureNotificationRateLimit()
    private val recording = RecorderStatus(RecorderState.RECORDING, "trip", true, 0.0, 0.0, true)

    @Test
    fun `progress posts are at least five seconds apart`() {
        val progress = recording.copy(distanceM = 80.0)
        assertTrue(limit.shouldPost(recording, 0))
        assertFalse(limit.shouldPost(progress, 1_000_000_000L))
        assertFalse(limit.shouldPost(progress, 4_999_999_999L))
        assertTrue(limit.shouldPost(progress, 5_000_000_000L))
        assertFalse(limit.shouldPost(progress.copy(distanceM = 100.0), 9_999_999_999L))
        assertTrue(limit.shouldPost(progress.copy(distanceM = 100.0), 10_000_000_000L))
    }

    @Test
    fun `pause resume and stop post immediately and reset the progress interval`() {
        val paused = recording.copy(state = RecorderState.PAUSED)
        assertTrue(limit.shouldPost(recording, 0))
        assertTrue(limit.shouldPost(paused, 1_000_000_000L))
        assertFalse(limit.shouldPost(paused.copy(elapsedS = 5.0), 5_000_000_000L))
        assertTrue(limit.shouldPost(recording, 5_000_000_001L))
        assertTrue(limit.shouldPost(recording.copy(state = RecorderState.FINISHING), 5_000_000_002L))
    }

    @Test
    fun `a new trip posts immediately even when the recorder state is unchanged`() {
        assertTrue(limit.shouldPost(recording, 0))
        assertTrue(limit.shouldPost(recording.copy(tripId = "next"), 1))
    }
}
