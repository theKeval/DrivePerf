package com.driveperf.feature.drives

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Labels for the trip list. INTERIM (DRIVE-72): number, unit and date formatting belongs to `:shared:presentation`
 * (ADR-002 section 4); swap for the shared formatters when DRIVE-62 lands.
 */
internal class TripFormatting(private val zone: ZoneId, private val locale: Locale, private val today: () -> LocalDate) {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone)

    /** "Today, 7:42 AM", "Yesterday, 5:10 PM" or a full date for anything older. */
    fun start(startedWallMs: Long): String {
        val instant = Instant.ofEpochMilli(startedWallMs)
        return when (instant.atZone(zone).toLocalDate()) {
            today() -> "Today, ${time.format(instant)}"
            today().minusDays(1) -> "Yesterday, ${time.format(instant)}"
            else -> dateTime.format(instant)
        }
    }

    /** "42 min", "1 h 05 min" or "< 1 min". */
    fun duration(durationS: Long): String {
        val minutes = (durationS / SECONDS_PER_MINUTE).toInt()
        return when {
            minutes < 1 -> "< 1 min"
            minutes < MINUTES_PER_HOUR -> "$minutes min"
            else -> String.format(Locale.US, "%d h %02d min", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        }
    }

    fun distance(distanceM: Double): String = String.format(Locale.US, "%.1f km", distanceM / METRES_PER_KM)

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
        const val METRES_PER_KM = 1000.0
    }
}
