package com.driveperf.domain

import com.driveperf.domain.capture.AutoDetectHealth
import com.driveperf.domain.capture.AutoDetectPause
import com.driveperf.domain.capture.AutoDetectPauseLength
import com.driveperf.domain.capture.NoOpAutoDetectResumeNotice
import com.driveperf.domain.capture.NoOpAutoDetectWatchdog
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.capture.WakeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** The recorder contract's defaults: fakes that implement only the original calls keep working (F-CAP-14, F-CAP-15). */
class RecorderContractTest {
    /** A recorder with only the required members; every newer call uses its default body. */
    private class MinimalRecorder : TripRecorder {
        val launches = mutableListOf<String>()
        override val status: StateFlow<RecorderStatus> = MutableStateFlow(RecorderStatus.IDLE)

        override fun onLaunch() {
            launches += "launch"
        }

        override fun onWake(event: WakeEvent) = Unit

        override fun stop() = Unit

        override fun markPassenger(keep: Boolean) = Unit

        override fun setAutoDetect(enabled: Boolean) = Unit
    }

    @Test
    fun theNewerCallsDefaultToNothingAndLaunchWithAReasonIsALaunch() {
        val recorder = MinimalRecorder()
        recorder.onLaunch(RearmReason.BOOT)
        recorder.onForeground()
        recorder.onWatchdogOpened()
        recorder.setTriggersAvailable(false)
        recorder.setInDriveScreenVisible(true)
        recorder.pauseAutoDetect(AutoDetectPauseLength.ONE_HOUR, 3_600_000L)
        recorder.resumeAutoDetect()
        recorder.undoPauseAutoDetect()
        assertEquals(listOf("launch"), recorder.launches)
        assertEquals(RecorderStatus.IDLE, recorder.status.value)
    }

    @Test
    fun theNoOpPortsScheduleNothing() {
        NoOpAutoDetectWatchdog.schedule(1L)
        NoOpAutoDetectWatchdog.cancel()
        NoOpAutoDetectResumeNotice.schedule(1L)
        NoOpAutoDetectResumeNotice.cancel()
    }

    @Test
    fun aStatusIsNotPausedUntilTheCoreSaysSo() {
        assertEquals(AutoDetectHealth.ON, RecorderStatus.IDLE.autoDetectHealth)
        assertNull(RecorderStatus.IDLE.autoDetectPausedUntilWallMs)
        val off = RecorderStatus.IDLE.copy(autoDetectEnabled = false)
        assertEquals(AutoDetectHealth.ON, off.autoDetectHealth, "copy keeps the health it was given")
        assertEquals(AutoDetectHealth.OFF, RecorderStatus(off.state, null, false, 0.0, 0.0, false).autoDetectHealth)
        val paused = RecorderStatus.IDLE.copy(autoDetectHealth = AutoDetectHealth.PAUSED, autoDetectPausedUntilWallMs = 9L)
        assertEquals(9L, paused.autoDetectPausedUntilWallMs)
    }

    @Test
    fun aPauseIsAValue() {
        val pause = AutoDetectPause(AutoDetectPauseLength.UNTIL_MORNING, pausedAtWallMs = 1L, untilWallMs = 2L)
        assertEquals(pause, pause.copy())
        assertNotEquals(pause, pause.copy(untilWallMs = null))
        assertEquals(3, AutoDetectPauseLength.entries.size)
    }
}
