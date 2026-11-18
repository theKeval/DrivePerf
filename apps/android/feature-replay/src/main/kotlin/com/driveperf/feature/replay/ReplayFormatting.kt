package com.driveperf.feature.replay

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DisplayUnit

/** Readout text for S-RPL-01. Units go through [UnitSystem.display] (DRIVE-153). Speed is neutral text (D5/D6). */
internal object ReplayFormatting {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600

    /** "0:00", "12:05", "1:02:09". */
    fun clock(seconds: Double): String {
        val s = seconds.coerceAtLeast(0.0).toInt()
        val h = s / SECONDS_PER_HOUR
        val m = (s % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val sec = s % SECONDS_PER_MINUTE
        val ss = sec.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    /** Speed as a number in the user's unit, or null without GPS (the readout then shows "—"). */
    fun speed(speedMps: Double?, units: UnitSystem): String? = speedMps?.let { units.display(DisplayUnit.KM_PER_HOUR).format(it) }

    fun speedUnit(units: UnitSystem): String = units.display(DisplayUnit.KM_PER_HOUR).symbol

    fun distance(metres: Double, units: UnitSystem): String = units.display(DisplayUnit.KILOMETRES).format(metres)

    fun distanceUnit(units: UnitSystem): String = units.display(DisplayUnit.KILOMETRES).symbol

    /** Whole minutes for the scrubber's spoken position ("12 min of 33 min"). */
    fun minutes(seconds: Double): Int = (seconds / SECONDS_PER_MINUTE).toInt()
}
