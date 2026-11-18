package com.driveperf.feature.capture

import com.driveperf.domain.capture.AutoDetectPauseLength
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which day a pause ends on, seen from now: the words "until 8:30 pm", "until 6:00 tomorrow", "until Saturday 6:00". */
enum class PauseDay { TODAY, TOMORROW, LATER }

/**
 * Pure rules for S-HOME-03 (F-CAP-15): when each choice ends and how its end is named. The shared core keeps the pause
 * and ends it; these only turn the user's choice into a UTC time in the phone's time zone.
 */
object PauseRules {
    /** "Until tomorrow morning" ends at this local hour (PRD F-CAP-15 AC1: tomorrow 06:00). */
    const val MORNING_HOUR: Int = 6

    private const val HOUR_MS = 3_600_000L

    /** The choice the sheet opens with (S-HOME-03 layout). */
    val DEFAULT: AutoDetectPauseLength = AutoDetectPauseLength.UNTIL_MORNING

    /**
     * When a pause chosen at [nowMs] ends: an hour later; at 06:00 local time on the next calendar day (DST-safe: a
     * zone with no 06:00 that day moves it forward); or never (null, until the user turns it back on).
     */
    fun untilWallMs(length: AutoDetectPauseLength, nowMs: Long, zone: ZoneId): Long? = when (length) {
        AutoDetectPauseLength.ONE_HOUR -> nowMs + HOUR_MS
        AutoDetectPauseLength.UNTIL_MORNING -> Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1)
            .atTime(LocalTime.of(MORNING_HOUR, 0)).atZone(zone).toInstant().toEpochMilli()
        AutoDetectPauseLength.UNTIL_RESUMED -> null
    }

    /** The day [untilMs] falls on, in [zone], seen from [nowMs]. */
    fun day(untilMs: Long, nowMs: Long, zone: ZoneId): PauseDay {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(untilMs).atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(today, end)) {
            0L -> PauseDay.TODAY
            1L -> PauseDay.TOMORROW
            else -> PauseDay.LATER
        }
    }
}
