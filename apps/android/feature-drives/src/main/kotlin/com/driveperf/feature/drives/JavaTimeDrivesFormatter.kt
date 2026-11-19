package com.driveperf.feature.drives

import com.driveperf.presentation.drives.DrivesFormatter
import com.driveperf.presentation.drives.DrivesPlatform
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Android's dates and times for the shared Drives presenter: `java.time` in the device's [zone] and [locale]. */
class JavaTimeDrivesFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) : DrivesFormatter {
    override val platform: DrivesPlatform = DrivesPlatform.ANDROID

    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).withZone(zone)
    private val dayAndMonth = DateTimeFormatter.ofPattern("d MMMM", locale).withZone(zone)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale).withZone(zone)
    private val month = DateTimeFormatter.ofPattern("MMM", locale).withZone(zone)
    private val firstDay = WeekFields.of(locale).firstDayOfWeek

    private fun at(epochMs: Long): ZonedDateTime = Instant.ofEpochMilli(epochMs).atZone(zone)

    override fun time(epochMs: Long): String = time.format(Instant.ofEpochMilli(epochMs))

    override fun longDate(epochMs: Long): String = longDate.format(Instant.ofEpochMilli(epochMs))

    override fun dayAndMonth(epochMs: Long): String = dayAndMonth.format(Instant.ofEpochMilli(epochMs))

    override fun weekday(epochMs: Long): String = weekday.format(Instant.ofEpochMilli(epochMs))

    override fun month(epochMs: Long): String = month.format(Instant.ofEpochMilli(epochMs))

    override fun dayOfMonth(epochMs: Long): Int = at(epochMs).dayOfMonth

    override fun hour(epochMs: Long): Int = at(epochMs).hour

    override fun weekStart(epochMs: Long): Long =
        at(epochMs).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDay)).atStartOfDay(zone).toInstant().toEpochMilli()
}
