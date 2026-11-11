package com.driveperf.platform.location

import com.driveperf.domain.capture.LocationSample
import com.driveperf.foundation.Clock
import com.driveperf.platform.FreshnessFilter

/**
 * Drops stale and out-of-order fixes from the batches the fused provider delivers (spike 1 change 8). One instance per
 * capture session, so a restart never inherits the previous session's ordering state. Not thread-safe: the fused
 * provider calls it from one executor thread.
 */
internal class LocationBatchFilter(private val clock: Clock) {
    // A fix timestamped just before capture started is still part of the wake, so allow 2 s of slack.
    private val filter = FreshnessFilter(notBeforeNs = clock.monotonicNs() - START_ALLOWANCE_NS)

    fun fresh(samples: List<LocationSample>): List<LocationSample> {
        val now = clock.monotonicNs()
        return samples.filter { filter.accept(it.tMonoNs, now) }
    }

    private companion object {
        const val START_ALLOWANCE_NS = 2_000_000_000L
    }
}
