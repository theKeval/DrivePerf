package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.WatchdogState

/**
 * The F-CAP-14 watchdog policy (DTS pattern, A3 §1.5). Every check-in moves the notification [CaptureConfig.watchdogSilenceS]
 * ahead, so it fires only after that long without one. A notification that came due before this check-in was shown,
 * and the next one is then held back until [CaptureConfig.watchdogMinGapS] after it (at most once per 72 h).
 */
internal class WatchdogPolicy(private val cfg: CaptureConfig) {
    /** The new state after a check-in at [nowWallMs]; [fired] when the previous notification came due before it. */
    data class CheckIn(val state: WatchdogState, val fired: Boolean)

    fun checkIn(prev: WatchdogState, nowWallMs: Long): CheckIn {
        val due = prev.dueWallMs
        val fired = due != null && due <= nowWallMs
        val lastFired = if (fired) due else prev.lastFiredWallMs
        val afterSilence = nowWallMs + ms(cfg.watchdogSilenceS)
        val afterGap = lastFired?.let { it + ms(cfg.watchdogMinGapS) } ?: afterSilence
        return CheckIn(WatchdogState(nowWallMs, maxOf(afterSilence, afterGap), lastFired), fired)
    }

    /** Automatic detection turned off: nothing is due. The check-in time and the last firing are kept. */
    fun off(prev: WatchdogState): WatchdogState = prev.copy(dueWallMs = null)

    private fun ms(s: Double): Long = (s * MS_PER_S).toLong()

    private companion object {
        const val MS_PER_S = 1000.0
    }
}
