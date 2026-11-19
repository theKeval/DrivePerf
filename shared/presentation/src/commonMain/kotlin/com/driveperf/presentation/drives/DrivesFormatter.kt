package com.driveperf.presentation.drives

/** The platform the app runs on: the interrupted-trip copy names it (docs/design DRIVE-109, S-TRP-01). */
public enum class DrivesPlatform(internal val stoppedUs: String) {
    ANDROID("Android stopped DrivePerf in the background"),
    IOS("iOS ended DrivePerf in the background"),
}

/**
 * The only formatting the Drives presenter leaves to the app (ADR-002 section 4): dates and times in the user's time
 * zone, locale and calendar. Numbers, units and every sentence are shared. Each app supplies one (`java.time` on
 * Android, `Date.FormatStyle` and `Calendar` on iOS). All times are UTC epoch milliseconds.
 */
public interface DrivesFormatter {
    public val platform: DrivesPlatform

    /** The short local time: "8:14 a.m.". */
    public fun time(epochMs: Long): String

    /** The long local date: "December 5, 2025". */
    public fun longDate(epochMs: Long): String

    /** The day and the full month name, in the locale's order: "5 November". */
    public fun dayAndMonth(epochMs: Long): String

    /** The full weekday name: "Thursday". */
    public fun weekday(epochMs: Long): String

    /** The abbreviated month name: "Nov". */
    public fun month(epochMs: Long): String

    /** The local day of the month, 1–31. */
    public fun dayOfMonth(epochMs: Long): Int

    /** The local hour of the day, 0–23. */
    public fun hour(epochMs: Long): Int

    /** The local midnight that starts the calendar week holding [epochMs], on the locale's first weekday. */
    public fun weekStart(epochMs: Long): Long
}
