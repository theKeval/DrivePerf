package com.driveperf.di

import com.driveperf.domain.capture.TimeZoneProvider
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

/** The device's current zone, read through kotlinx-datetime. */
internal class SystemTimeZone : TimeZoneProvider {
    override fun zoneId(): String = TimeZone.currentSystemDefault().id

    override fun offsetMinAt(wallMs: Long): Int =
        TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochMilliseconds(wallMs)).totalSeconds / SECONDS_PER_MINUTE

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
