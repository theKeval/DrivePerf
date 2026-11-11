package com.driveperf.platform.activity

import com.driveperf.domain.capture.ActivitySample

/**
 * The most recent activity changes seen by this process, so `ActivitySource.queryHistory` can answer with exactly the
 * window asked and nothing more (Spike 1 change 3). It lives in memory: it covers a trip recorded in this process but
 * not one that began before the process was killed, for which the answer is an empty list, as the port allows. The
 * persisted history arrives with the background wake path (activity transitions), which is not part of this shell.
 */
internal class ActivityHistory(private val capacity: Int = DEFAULT_CAPACITY) {
    private val samples = ArrayDeque<ActivitySample>()

    @Synchronized
    fun add(sample: ActivitySample) {
        if (samples.size == capacity) samples.removeFirst()
        samples.addLast(sample)
    }

    /** Samples whose wall time lies in [[fromWallMs], [toWallMs]], oldest first. */
    @Synchronized
    fun between(fromWallMs: Long, toWallMs: Long): List<ActivitySample> =
        samples.filter { it.wallMs in fromWallMs..toWallMs }

    private companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
