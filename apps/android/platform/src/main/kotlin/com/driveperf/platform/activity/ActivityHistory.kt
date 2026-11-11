package com.driveperf.platform.activity

import com.driveperf.domain.capture.ActivitySample

/**
 * The most recent activity changes seen by this process, so `ActivitySource.queryHistory` can answer with exactly the
 * window asked and nothing more (Spike 1 change 3). It lives in memory: live updates while a trip records, plus the
 * activity transitions that wake the app (the wake path), which arrive in the same process just before the wake.
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
        samples.filter { it.wallMs in fromWallMs..toWallMs }.sortedBy { it.wallMs }

    private companion object {
        const val DEFAULT_CAPACITY = 256
    }
}

/** The process-wide history shared by the live source and the transition wake receiver. */
internal object ActivityLog {
    private val history = ActivityHistory()

    fun add(sample: ActivitySample) = history.add(sample)

    fun between(fromWallMs: Long, toWallMs: Long): List<ActivitySample> = history.between(fromWallMs, toWallMs)
}
