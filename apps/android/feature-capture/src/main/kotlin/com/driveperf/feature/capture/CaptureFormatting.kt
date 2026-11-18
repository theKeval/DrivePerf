package com.driveperf.feature.capture

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DisplayUnit

import java.util.Locale

/**
 * Number formatting for the live screens. INTERIM (DRIVE-72): formatting of numbers and units belongs to
 * `:shared:presentation` (ADR-002 section 4) so both platforms show identical values; swap these for the shared
 * formatters when DRIVE-62 lands.
 */
internal object CaptureFormatting {
    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60

    /** "7:05" under an hour, "1:02:09" above it. */
    fun elapsed(elapsedMs: Long): String {
        val total = (elapsedMs.coerceAtLeast(0) / 1000).toInt()
        val h = total / SECONDS_PER_HOUR
        val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val s = total % SECONDS_PER_MINUTE
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun distance(distanceM: Double, units: UnitSystem): String {
        val unit = units.display(DisplayUnit.KILOMETRES)
        return "${unit.format(distanceM)} ${unit.symbol}"
    }
}
