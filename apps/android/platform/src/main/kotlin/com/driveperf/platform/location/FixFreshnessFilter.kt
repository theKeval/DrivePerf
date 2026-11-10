package com.driveperf.platform.location

import java.util.concurrent.TimeUnit

/**
 * Drops stale and out-of-order fixes (spike 1 change 8: every cold launch delivered a cached fix up to 14 h old).
 *
 * A fix is accepted when it is not older than [maxAgeNs] at delivery, is not from before [notBeforeNs] (the moment
 * capture started, minus a small allowance), and is newer than the last accepted fix. Not thread-safe: use it from the
 * one coroutine that consumes the fixes.
 */
class FixFreshnessFilter(
    private val maxAgeNs: Long = DEFAULT_MAX_AGE_NS,
    private val notBeforeNs: Long = Long.MIN_VALUE,
) {
    private var lastAcceptedNs: Long = Long.MIN_VALUE

    fun accept(fixMonoNs: Long, nowMonoNs: Long): Boolean {
        val fresh = nowMonoNs - fixMonoNs <= maxAgeNs
        val afterStart = fixMonoNs >= notBeforeNs
        val ordered = fixMonoNs > lastAcceptedNs
        val ok = fresh && afterStart && ordered
        if (ok) lastAcceptedNs = fixMonoNs
        return ok
    }

    companion object {
        val DEFAULT_MAX_AGE_NS: Long = TimeUnit.SECONDS.toNanos(10)
    }
}
