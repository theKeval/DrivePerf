package com.driveperf.platform.capture

import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripClockTest {
    private fun status(state: RecorderState, id: String? = "t1", elapsedS: Double = 30.0) =
        RecorderStatus(state, id, isManual = true, elapsedS = elapsedS, distanceM = 0.0, autoDetectEnabled = true)

    @Test
    fun `the start is fixed once per trip and candidates are not shown`() {
        val clock = TripClock()
        assertNull(clock.startedWallMs(status(RecorderState.IDLE, id = null), 100_000L))
        assertNull(clock.startedWallMs(status(RecorderState.CANDIDATE), 100_000L))
        assertEquals(70_000L, clock.startedWallMs(status(RecorderState.RECORDING), 100_000L))
        assertEquals(70_000L, clock.startedWallMs(status(RecorderState.PAUSED, elapsedS = 31.7), 101_700L))
        assertEquals(200_000L, clock.startedWallMs(status(RecorderState.RECORDING, id = "t2", elapsedS = 0.0), 200_000L))
    }
}
