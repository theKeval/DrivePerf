package com.driveperf.feature.stats

import com.driveperf.domain.model.DistanceUnit
import com.driveperf.domain.model.TripSession
import com.driveperf.domain.model.TripState
import com.driveperf.ui.format.formatDistance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private const val METERS_PER_KILOMETER = 1000.0
private const val METERS_PER_MILE = 1609.344
private const val HOURS_PER_MILLISECOND = 1.0 / 3_600_000.0

data class StatsUiState(
    val isLoading: Boolean = true,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val hasAnyTrips: Boolean = false,
    val hasPartialData: Boolean = false,
    val summary: SummaryStats = SummaryStats(),
    val chartBars: List<ChartBarStat> = emptyList(),
    val longestTripRecord: PersonalRecord? = null,
    val fastestTripRecord: PersonalRecord? = null,
)

data class SummaryStats(
    val totalDistanceMeters: Double = 0.0,
    val totalTrips: Int = 0,
    val totalDurationMs: Long = 0L,
    val averageSpeedMetersPerHour: Double? = null,
)

data class ChartBarStat(
    val date: LocalDate,
    val distanceMeters: Double?,
    val isToday: Boolean,
)

data class PersonalRecord(
    val tripId: Long,
    val date: LocalDate,
    val distanceMeters: Double,
    val durationMs: Long,
    val averageSpeedMetersPerHour: Double,
)

internal data class StatsSnapshot(
    val hasAnyTrips: Boolean,
    val hasPartialData: Boolean,
    val summary: SummaryStats,
    val chartBars: List<ChartBarStat>,
    val longestTripRecord: PersonalRecord?,
    val fastestTripRecord: PersonalRecord?,
)

internal fun buildStatsSnapshot(
    trips: List<TripSession>,
    now: Instant,
    zoneId: ZoneId,
): StatsSnapshot {
    val validTrips = trips.filter { it.isValidForStats() }
    val hasPartialData = trips.any { it.isExcludedFromStats() }
    val today = now.atZone(zoneId).toLocalDate()
    val chartDates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val unknownChartDates = if (hasPartialData) {
        trips.asSequence()
            .filter { it.isExcludedFromStats() }
            .mapNotNull { trip -> Instant.ofEpochMilli(trip.startTimeMs).atZone(zoneId).toLocalDate() }
            .filter { it in chartDates }
            .toSet()
    } else {
        emptySet()
    }
    val distanceByDay = validTrips.groupBy { Instant.ofEpochMilli(it.startTimeMs).atZone(zoneId).toLocalDate() }
        .mapValues { (_, dayTrips) -> dayTrips.sumOf { max(it.distanceMeters, 0.0) } }

    val chartBars = chartDates.map { date ->
        val dayDistance = distanceByDay[date]
        ChartBarStat(
            date = date,
            distanceMeters = when {
                dayDistance != null -> dayDistance
                date in unknownChartDates -> null
                else -> 0.0
            },
            isToday = date == today,
        )
    }

    val totalDistanceMeters = validTrips.sumOf { max(it.distanceMeters, 0.0) }
    val totalDurationMs = validTrips.sumOf { (it.endTimeMs ?: it.startTimeMs) - it.startTimeMs }
    val avgSpeedMetersPerHour = totalDurationMs.takeIf { it > 0L }?.let {
        totalDistanceMeters / (it * HOURS_PER_MILLISECOND)
    }
    val summary = SummaryStats(
        totalDistanceMeters = totalDistanceMeters,
        totalTrips = validTrips.size,
        totalDurationMs = totalDurationMs,
        averageSpeedMetersPerHour = avgSpeedMetersPerHour,
    )

    val longestTrip = validTrips.maxWithOrNull(longestTripComparator)
    val fastestTrip = validTrips
        .mapNotNull { trip -> trip.averageSpeedMetersPerHour()?.let { trip to it } }
        .maxWithOrNull(fastestTripComparator)
        ?.first

    return StatsSnapshot(
        hasAnyTrips = validTrips.isNotEmpty(),
        hasPartialData = hasPartialData,
        summary = summary,
        chartBars = chartBars,
        // Deterministic tie-breaker for equal metrics: earliest trip wins, then lowest ID.
        longestTripRecord = longestTrip?.toPersonalRecord(zoneId),
        fastestTripRecord = fastestTrip?.toPersonalRecord(zoneId),
    )
}

internal fun SummaryStats.formatTotalDistance(distanceUnit: DistanceUnit, locale: Locale): String =
    formatDistance(totalDistanceMeters, distanceUnit, locale)

internal fun SummaryStats.formatTotalTrips(locale: Locale): String =
    String.format(locale, "%,d", totalTrips)

internal fun SummaryStats.formatTotalDuration(): String {
    val totalMinutes = totalDurationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

internal fun SummaryStats.formatAverageSpeed(
    distanceUnit: DistanceUnit,
    locale: Locale,
    placeholder: String,
): String {
    val speedMetersPerHour = averageSpeedMetersPerHour ?: return placeholder
    val speed = speedMetersPerHour.toSpeedIn(distanceUnit)
    return String.format(locale, "%.1f %s", speed, distanceUnit.speedSuffix)
}

internal fun PersonalRecord.formatValue(type: RecordType, distanceUnit: DistanceUnit, locale: Locale): String =
    when (type) {
        RecordType.LONGEST_TRIP -> formatDistance(distanceMeters, distanceUnit, locale)
        RecordType.FASTEST_AVERAGE_SPEED -> {
            val speed = averageSpeedMetersPerHour.toSpeedIn(distanceUnit)
            String.format(locale, "%.1f %s", speed, distanceUnit.speedSuffix)
        }
    }

internal fun PersonalRecord.formatMeta(distanceUnit: DistanceUnit, locale: Locale): String {
    val month = date.month.getDisplayName(TextStyle.SHORT, locale)
    val dateLabel = "$month ${date.dayOfMonth}, ${date.year}"
    return "$dateLabel · ${formatDuration(durationMs)}"
}

internal fun formatChartDistance(distanceMeters: Double, distanceUnit: DistanceUnit, locale: Locale): String {
    val value = when (distanceUnit) {
        DistanceUnit.KILOMETERS -> distanceMeters / METERS_PER_KILOMETER
        DistanceUnit.MILES -> distanceMeters / METERS_PER_MILE
    }
    return String.format(locale, "%.1f", value)
}

internal fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * A trip is valid for statistics only when it is completed, not marked as passenger,
 * has non-negative recorded distance, and has a strictly positive duration.
 */
private fun TripSession.isValidForStats(): Boolean {
    val end = endTimeMs ?: return false
    return state == TripState.COMPLETED &&
        !isPassenger &&
        distanceMeters >= 0.0 &&
        end > startTimeMs
}

private fun TripSession.isExcludedFromStats(): Boolean = !isValidForStats()

private fun Double.toSpeedIn(distanceUnit: DistanceUnit): Double = when (distanceUnit) {
    DistanceUnit.KILOMETERS -> this / METERS_PER_KILOMETER
    DistanceUnit.MILES -> this / METERS_PER_MILE
}

private fun TripSession.averageSpeedMetersPerHour(): Double? {
    val end = endTimeMs ?: return null
    val durationMs = end - startTimeMs
    if (durationMs <= 0L || distanceMeters <= 0.0) return null
    return distanceMeters / (durationMs * HOURS_PER_MILLISECOND)
}

private fun TripSession.toPersonalRecord(zoneId: ZoneId): PersonalRecord {
    val end = endTimeMs ?: startTimeMs
    val durationMs = max(0L, end - startTimeMs)
    return PersonalRecord(
        tripId = id,
        date = Instant.ofEpochMilli(startTimeMs).atZone(zoneId).toLocalDate(),
        distanceMeters = distanceMeters,
        durationMs = durationMs,
        averageSpeedMetersPerHour = averageSpeedMetersPerHour() ?: 0.0,
    )
}

private val longestTripComparator = Comparator<TripSession> { first, second ->
    when {
        first.distanceMeters != second.distanceMeters -> first.distanceMeters.compareTo(second.distanceMeters)
        // Earlier start timestamps win ties, so we intentionally reverse the comparison here.
        first.startTimeMs != second.startTimeMs -> second.startTimeMs.compareTo(first.startTimeMs)
        // Lower IDs win ties; reverse here because maxWithOrNull picks the "largest" value.
        else -> second.id.compareTo(first.id)
    }
}

private val fastestTripComparator = Comparator<Pair<TripSession, Double>> { first, second ->
    when {
        first.second != second.second -> first.second.compareTo(second.second)
        // Earlier start timestamps win ties, so we intentionally reverse the comparison here.
        first.first.startTimeMs != second.first.startTimeMs -> second.first.startTimeMs.compareTo(first.first.startTimeMs)
        // Lower IDs win ties; reverse here because maxWithOrNull picks the "largest" value.
        else -> second.first.id.compareTo(first.first.id)
    }
}

enum class RecordType {
    LONGEST_TRIP,
    FASTEST_AVERAGE_SPEED,
}
