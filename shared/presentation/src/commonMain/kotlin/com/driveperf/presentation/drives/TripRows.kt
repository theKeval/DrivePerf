package com.driveperf.presentation.drives

import com.driveperf.domain.score.ScoreLine
import com.driveperf.domain.score.ScoreLines
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.DisplayUnit
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.openQuestion
import com.driveperf.presentation.score.ComparisonCopy
import com.driveperf.presentation.score.ScoreCopy
import kotlin.math.roundToInt

/** How a listed trip ended (F-CAP-06): complete, cut short by the OS, or a manual trip too short to score (§1.6). */
internal enum class ListedStatus { FINALISED, INTERRUPTED, SHORT }

private const val SHORT_TRIP_M = 500.0
private const val SHORT_TRIP_S = 120.0
private const val MS_PER_S = 1_000L
private const val SECONDS_PER_MINUTE = 60.0
private const val MINUTES_PER_HOUR = 60
private const val HALF_DAY_MS = 12 * 60 * 60 * 1_000L
private const val DAY_MS = 2 * HALF_DAY_MS
private const val DAYS_TO_WEEK_END = 6

internal val Trip.distance: Double get() = distanceM ?: 0.0
internal val Trip.seconds: Double get() = durationS ?: 0.0

/** When the recording stopped: the end time, or the start plus the duration. */
internal val Trip.endMs: Long get() = endedAt ?: (startedAt + (seconds * MS_PER_S).toLong())

/** A manual trip under 500 m or 120 s is "Short trip" (pipeline-and-scoring.md §1.6); automatic trips never are. */
internal val Trip.listedStatus: ListedStatus
    get() = when {
        status == TripStatus.INTERRUPTED -> ListedStatus.INTERRUPTED
        source == TripSource.MANUAL && (distance < SHORT_TRIP_M || seconds < SHORT_TRIP_S) -> ListedStatus.SHORT
        else -> ListedStatus.FINALISED
    }

/** Seconds as whole minutes, rounded half up; never negative. */
internal fun wholeMinutes(seconds: Double): Int = (maxOf(seconds, 0.0) / SECONDS_PER_MINUTE).roundToInt()

/** "12.4 km" or "7.7 mi". */
internal fun distanceText(metres: Double, units: UnitSystem): String {
    val unit = units.display(DisplayUnit.KILOMETRES)
    return "${unit.format(metres)} ${unit.symbol}"
}

/** "< 1 min", "42 min" or "1 h 05 min" (brand.md). */
internal fun durationText(seconds: Double): String {
    val m = wholeMinutes(seconds)
    return when {
        m < 1 -> if (seconds > 0) "< 1 min" else "0 min"
        m < MINUTES_PER_HOUR -> "$m min"
        else -> "${m / MINUTES_PER_HOUR} h ${(m % MINUTES_PER_HOUR).toString().padStart(2, '0')} min"
    }
}

/** [durationText] as a screen reader says it: "less than a minute", "1 hour 5 minutes". */
internal fun spokenDuration(seconds: Double): String {
    val m = wholeMinutes(seconds)
    fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    return when {
        m < 1 && seconds > 0 -> "less than a minute"
        m < MINUTES_PER_HOUR -> count(m, "minute")
        m % MINUTES_PER_HOUR == 0 -> count(m / MINUTES_PER_HOUR, "hour")
        else -> "${count(m / MINUTES_PER_HOUR, "hour")} ${count(m % MINUTES_PER_HOUR, "minute")}"
    }
}

/** "1 drive", "3 drives". */
internal fun drivesCount(n: Int): String = if (n == 1) "1 drive" else "$n drives"

/**
 * Builds the trip cards and the week sections (S-HOME-01, S-HIS-01) at [nowMs]. [scores] holds each trip's score
 * line (score badge, route, car); a card names its car only when the user has more than one.
 */
internal class TripRows(
    private val units: UnitSystem,
    private val format: DrivesFormatter,
    private val nowMs: Long,
    private val scores: ScoreLines = ScoreLines.EMPTY,
) {
    private val score = TripScore(units, format) { null }

    /** "Thursday morning" */
    fun partOfDayTitle(trip: Trip): String = "${format.weekday(trip.startedAt)} ${partOfDay(format.hour(trip.startedAt))}"

    /** The route label ("The Commute", "Home → Work") when the trip has one, else "Thursday morning". */
    fun title(trip: Trip): String = label(trip) ?: partOfDayTitle(trip)

    /** "Thursday morning · 5 Nov" under a route label (components.md §12), else "5 Nov · 8:14 a.m.". */
    fun subtitle(trip: Trip): String = if (label(trip) != null) {
        "${partOfDayTitle(trip)} · ${format.dayOfMonth(trip.startedAt)} ${format.month(trip.startedAt)}"
    } else {
        "${format.dayOfMonth(trip.startedAt)} ${format.month(trip.startedAt)} · ${format.time(trip.startedAt)}"
    }

    /**
     * The trip's own label, else its named route's, else "From → To" from its place labels; null without any (no
     * geocoder in v1, so unnamed places have no label).
     */
    fun label(trip: Trip): String? {
        val from = trip.fromLabel?.takeIf { it.isNotBlank() }
        val to = trip.toLabel?.takeIf { it.isNotBlank() }
        return trip.labelOverride?.takeIf { it.isNotBlank() }
            ?: ComparisonCopy.routeName(scores.lines[trip.id]?.route)
            ?: if (from != null && to != null) "$from → $to" else null
    }

    fun line(trip: Trip): ScoreLine? = scores.lines[trip.id]

    fun row(trip: Trip): TripRowUi {
        val title = title(trip)
        val line = line(trip)
        val car = line?.car?.takeIf { scores.carCount > 1 }
        val distance = distanceText(trip.distance, units)
        val status = trip.listedStatus
        val note = when (status) {
            ListedStatus.SHORT -> "Short trip · not scored"
            ListedStatus.INTERRUPTED -> "${format.platform.stoppedUs}. Saved up to ${format.time(trip.endMs)}."
            ListedStatus.FINALISED -> "Started by you".takeIf { trip.source == TripSource.MANUAL }
        }
        val kindLabel = TripCopy.kindLabel(trip)
        val quality = trip.quality?.badge
        return TripRowUi(
            id = trip.id,
            title = title,
            subtitle = subtitle(trip),
            distance = distance,
            duration = durationText(trip.seconds),
            note = note,
            accessibilityLabel = listOfNotNull(
                title, if (title == partOfDayTitle(trip)) null else partOfDayTitle(trip), distance, spokenDuration(trip.seconds),
                score.spokenBadge(trip, line), car, kindLabel, note, quality?.let { "${TripCopy.badgeWord(it)} data" },
            ).joinToString(", "),
            quality = quality,
            interrupted = status == ListedStatus.INTERRUPTED,
            short = status == ListedStatus.SHORT,
            question = trip.openQuestion,
            kindLabel = kindLabel,
            score = score.badge(trip, line),
            car = car,
        )
    }


    /** [trips] (newest first) and their [rows], grouped by the locale's calendar week. */
    fun sections(trips: List<Trip>, rows: List<TripRowUi>): List<TripSectionUi> =
        trips.indices.groupBy { format.weekStart(trips[it].startedAt) }.map { (weekStart, indices) ->
            val scored = indices.map { trips[it] }.filter { it.scoresShown }.mapNotNull { line(it)?.smoothness }
            val average = if (scored.isEmpty()) null else (scored.sum().toDouble() / scored.size).roundToInt()
            val title = weekTitle(weekStart)
            TripSectionUi(
                id = weekStart.toString(),
                title = title,
                meta = drivesCount(indices.size) + (average?.let { " · avg $it" } ?: ""),
                rows = indices.map(rows::get),
                accessibilityLabel = "$title, ${drivesCount(indices.size)}" + (average?.let { ", average $it" } ?: ""),
            )
        }

    /** S-HIS-01 footer: "That’s every drive since 5 November." */
    fun since(oldest: Trip): String = "That’s every drive since ${format.dayAndMonth(oldest.startedAt)}."

    /** "This week", "Last week", "19–25 Oct" or "27 Oct – 2 Nov". */
    private fun weekTitle(weekStart: Long): String {
        val thisWeek = format.weekStart(nowMs)
        return when (weekStart) {
            thisWeek -> "This week"
            format.weekStart(thisWeek - 1) -> "Last week"
            else -> weekRange(weekStart)
        }
    }

    /** "19–25 Oct" or "27 Oct – 2 Nov". */
    private fun weekRange(weekStart: Long): String {
        // Midday on the last day, so a daylight-saving change in the week cannot move it to another day.
        val end = weekStart + DAYS_TO_WEEK_END * DAY_MS + HALF_DAY_MS
        val startDay = format.dayOfMonth(weekStart)
        val endDay = format.dayOfMonth(end)
        return if (format.month(weekStart) == format.month(end)) {
            "$startDay–$endDay ${format.month(end)}"
        } else {
            "$startDay ${format.month(weekStart)} – $endDay ${format.month(end)}"
        }
    }

    private companion object {
        const val MORNING = 5
        const val MIDDAY = 12
        const val AFTERNOON = 14
        const val EVENING = 17
        const val NIGHT = 21

        /** The part-of-day words used across Stories and lists. */
        fun partOfDay(hour: Int): String = when (hour) {
            in MORNING until MIDDAY -> "morning"
            in MIDDAY until AFTERNOON -> "midday"
            in AFTERNOON until EVENING -> "afternoon"
            in EVENING until NIGHT -> "evening"
            else -> "night"
        }
    }
}
