package com.driveperf.ui.format

import com.driveperf.domain.model.DistanceUnit
import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import java.util.Locale

private const val METERS_PER_KILOMETER = 1000.0
private const val METERS_PER_MILE = 1609.344
private const val KILOMETERS_PER_MILE = 1.609344
// Matches the exact speeding detail strings currently stored by the app, e.g. "Speed: 120 km/h"
// or "Speed: 75 mph". If that persisted format changes, we intentionally fall back to the
// original text instead of guessing at alternate localized formats.
private val speedingPattern = Regex("""^Speed: ([0-9]+(?:\.[0-9]+)?) (km/h|mph)$""")

fun formatDistance(
    distanceMeters: Double,
    distanceUnit: DistanceUnit,
    locale: Locale = Locale.getDefault(),
): String {
    val distance = when (distanceUnit) {
        DistanceUnit.KILOMETERS -> distanceMeters / METERS_PER_KILOMETER
        DistanceUnit.MILES -> distanceMeters / METERS_PER_MILE
    }
    return String.format(locale, "%.1f %s", distance, distanceUnit.distanceSuffix)
}

fun formatDrivingEventDetails(
    event: DrivingEvent,
    distanceUnit: DistanceUnit,
    locale: Locale = Locale.getDefault(),
): String = when (event.type) {
    EventType.SPEEDING -> formatSpeedDetails(event.details, distanceUnit, locale)
    else -> event.details
}

private fun formatSpeedDetails(
    details: String,
    distanceUnit: DistanceUnit,
    locale: Locale,
): String {
    val speedKmh = parseStoredSpeedKmh(details) ?: return details
    val convertedValue = when (distanceUnit) {
        DistanceUnit.KILOMETERS -> speedKmh
        DistanceUnit.MILES -> speedKmh / KILOMETERS_PER_MILE
    }
    return String.format(locale, "Speed: %.0f %s", convertedValue, distanceUnit.speedSuffix)
}

private fun parseStoredSpeedKmh(details: String): Double? {
    val match = speedingPattern.matchEntire(details) ?: return null
    val sourceValue = match.groupValues[1].toDoubleOrNull() ?: return null
    return when (match.groupValues[2]) {
        DistanceUnit.MILES.speedSuffix -> sourceValue * KILOMETERS_PER_MILE
        else -> sourceValue
    }
}
