package com.driveperf.presentation.drives

import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Toronto, where the fixtures' days and weeks are counted. */
val TORONTO: ZoneId = ZoneId.of("America/Toronto")

/** Wall time in Toronto as epoch milliseconds. */
fun toronto(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(TORONTO).toInstant().toEpochMilli()

/** `java.time` formatting, as the Android app does it; weeks start on [firstDay]. */
class JavaTimeFormatter(
    private val zone: ZoneId = TORONTO,
    private val locale: Locale = Locale.US,
    private val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    override val platform: DrivesPlatform = DrivesPlatform.ANDROID,
) : DrivesFormatter {
    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, locale).withZone(zone)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).withZone(zone)
    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)

    override fun time(epochMs: Long): String = time.format(Instant.ofEpochMilli(epochMs))
    override fun longDate(epochMs: Long): String = longDate.format(Instant.ofEpochMilli(epochMs))
    override fun dayAndMonth(epochMs: Long): String = pattern("d MMMM").format(Instant.ofEpochMilli(epochMs))
    override fun weekday(epochMs: Long): String = pattern("EEEE").format(Instant.ofEpochMilli(epochMs))
    override fun month(epochMs: Long): String = pattern("MMM").format(Instant.ofEpochMilli(epochMs))
    override fun dayOfMonth(epochMs: Long): Int = at(epochMs).dayOfMonth
    override fun hour(epochMs: Long): Int = at(epochMs).hour
    override fun weekStart(epochMs: Long): Long =
        at(epochMs).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDay)).atStartOfDay(zone).toInstant().toEpochMilli()
}

class FixedClock(var nowMs: Long) : Clock {
    override fun monotonicNs(): Long = 0L
    override fun wallMs(): Long = nowMs
}

/** The core's corrections port, recording what the presenter sends. */
class FakeCorrections : TripCorrections {
    val notDrives = MutableStateFlow<List<Trip>>(emptyList())
    val answers = mutableListOf<Pair<String, SuspectAnswer>>()
    val corrections = mutableListOf<Pair<String, TripCorrection>>()

    override fun observeNotDrives() = notDrives

    override suspend fun answerSuspect(tripId: String, answer: SuspectAnswer): Trip? {
        answers += tripId to answer
        return null
    }

    override suspend fun correct(tripId: String, to: TripCorrection): Trip? {
        corrections += tripId to to
        return null
    }

    override suspend fun purgeExpiredNotDrives(): Int = 0
}

/** Wednesday 5 Nov 2025, 05:54 in Toronto (10:54 UTC). */
const val START = 1_762_340_040_000L

fun quality(
    badge: QualityBadge,
    reason: QualityReason? = null,
    imuHz: Double = 50.0,
    spans: List<TripSegment> = emptyList(),
    estimatedStartM: Double? = 429.0,
) = TripQuality(badge, reason, Placement.MOUNTED, imuHz, 96.6, 40.0, estimatedStartM, 1_860.0, 120.0, 1_980.0, spans)

@Suppress("LongParameterList") // one named knob per trip state the tests vary
fun trip(
    id: String = "t",
    startedAt: Long = START,
    durationS: Double = 1_980.0,
    distanceM: Double = 38_950.0,
    status: TripStatus = TripStatus.FINALISED,
    source: TripSource = TripSource.AUTO,
    quality: TripQuality? = null,
    suspect: Suspect = Suspect.NONE,
    kind: TripKind = TripKind.DRIVER,
    correction: TripCorrection? = null,
    estimatedM: Double? = null,
    endedAt: Long? = startedAt + (durationS * 1_000).toLong(),
) = Trip(
    id = id, status = status, kind = kind, source = source, startedAt = startedAt, startTzOffsetMin = -300,
    endedAt = endedAt, distanceM = distanceM, estimatedDistanceM = estimatedM, durationS = durationS, movingS = 1_700.0,
    stopCount = 2, maxSpeedMps = 28.6, quality = quality, suspect = suspect, correction = correction,
    correctedAt = correction?.let { startedAt }, createdAt = 0L, updatedAt = 0L,
)
