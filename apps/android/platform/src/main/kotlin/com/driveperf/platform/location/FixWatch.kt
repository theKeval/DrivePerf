package com.driveperf.platform.location

import com.driveperf.domain.capture.LocationSink
import com.driveperf.domain.capture.LocationSource
import com.driveperf.foundation.Clock

/** How the GNSS stream looks right now. Shown as a status, never as a score. */
enum class GpsStatus { SEARCHING, GOOD, LOST }

/**
 * Wraps the [LocationSource] the core drives and remembers when the last fix arrived, so the in-drive screen and the
 * notification can say "Finding GPS…" or "GPS lost" (S-DRV-01). The core's `RecorderStatus` has no GNSS state.
 */
class FixWatch(private val delegate: LocationSource, private val clock: Clock) : LocationSource {
    @Volatile private var lastFixMonoNs: Long? = null

    override fun start(sink: LocationSink) {
        lastFixMonoNs = null
        delegate.start { fixes ->
            fixes.maxOfOrNull { it.tMonoNs }?.let { lastFixMonoNs = it }
            sink.onLocations(fixes)
        }
    }

    override fun stop() {
        delegate.stop()
    }

    fun setBatchingEnabled(enabled: Boolean) {
        (delegate as? FusedLocationSource)?.setBatchingEnabled(enabled)
    }

    /** Searching until the first fix of this start, lost when the newest is older than [lostAfterNs]. */
    fun status(lostAfterNs: Long = LOST_AFTER_NS): GpsStatus {
        val last = lastFixMonoNs ?: return GpsStatus.SEARCHING
        return if (clock.monotonicNs() - last > lostAfterNs) GpsStatus.LOST else GpsStatus.GOOD
    }

    private companion object {
        const val LOST_AFTER_NS = 10_000_000_000L
    }
}
