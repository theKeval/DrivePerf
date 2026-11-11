package com.driveperf.platform.motion

import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.MotionSink

/**
 * Groups the one-at-a-time samples the sensor thread produces into the batches [MotionSink] takes, so the core is
 * called a few times a second instead of fifty. A batch is handed over once it spans [windowNs] of sensor time.
 * Not thread-safe: only the sensor thread touches it.
 */
internal class MotionBatcher(private val sink: MotionSink, private val windowNs: Long = DEFAULT_WINDOW_NS) {
    private val pending = ArrayList<MotionSample>()

    fun add(sample: MotionSample) {
        pending += sample
        if (sample.tMonoNs - pending.first().tMonoNs >= windowNs) flush()
    }

    private fun flush() {
        val batch = pending.toList()
        pending.clear()
        sink.onMotion(batch)
    }

    companion object {
        /** 200 ms: short enough that the core sees data promptly, long enough to cut call overhead tenfold at 50 Hz. */
        const val DEFAULT_WINDOW_NS = 200_000_000L
    }
}
