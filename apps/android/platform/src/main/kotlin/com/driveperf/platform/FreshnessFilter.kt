package com.driveperf.platform

import java.util.concurrent.TimeUnit

/**
 * Drops stale, future and out-of-order samples before they reach a trip file (spike 1 change 8: every cold launch
 * delivered a cached fix up to 14 h old). The Android emulator shows the same fault on motion sensors: the first
 * samples after registering carry timestamps of zero or minutes away from now.
 *
 * A sample is accepted when it is not older than [maxAgeNs] at delivery, not more than [maxFutureNs] ahead of the clock,
 * not from before [notBeforeNs] (when capture started, minus a small allowance), and newer than the last accepted
 * sample. Not thread-safe: use it from the one coroutine that consumes the samples.
 */
class FreshnessFilter(
    private val maxAgeNs: Long = DEFAULT_MAX_AGE_NS,
    private val notBeforeNs: Long = Long.MIN_VALUE,
    private val maxFutureNs: Long = DEFAULT_MAX_FUTURE_NS,
) {
    private var lastAcceptedNs: Long = Long.MIN_VALUE

    fun accept(sampleMonoNs: Long, nowMonoNs: Long): Boolean {
        val fresh = nowMonoNs - sampleMonoNs <= maxAgeNs
        val notFuture = sampleMonoNs - nowMonoNs <= maxFutureNs
        val afterStart = sampleMonoNs >= notBeforeNs
        val ordered = sampleMonoNs > lastAcceptedNs
        val ok = fresh && notFuture && afterStart && ordered
        if (ok) lastAcceptedNs = sampleMonoNs
        return ok
    }

    companion object {
        /** GNSS fixes: a fix this old at delivery is a cached one. */
        val DEFAULT_MAX_AGE_NS: Long = TimeUnit.SECONDS.toNanos(10)

        /** IMU samples are batched for at most 0.5 s, so two seconds is generous. */
        val MOTION_MAX_AGE_NS: Long = TimeUnit.SECONDS.toNanos(2)
        private val DEFAULT_MAX_FUTURE_NS: Long = TimeUnit.SECONDS.toNanos(1)
    }
}
