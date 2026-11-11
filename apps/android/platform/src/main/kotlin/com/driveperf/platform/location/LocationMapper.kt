package com.driveperf.platform.location

import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.driveperf.domain.capture.FixProvider
import com.driveperf.domain.capture.LocationSample

/**
 * Converts an Android [Location] into the sample contract. Android already reports m/s, metres and degrees.
 * [nowElapsedNs] is `SystemClock.elapsedRealtimeNanos()` at delivery, for the fix's age (F-DQ-03).
 */
internal fun Location.toSample(nowElapsedNs: Long): LocationSample = LocationSample(
    tMonoNs = elapsedRealtimeNanos,
    wallMs = time,
    lat = latitude,
    lng = longitude,
    altM = if (hasAltitude()) altitude else null,
    hAccM = if (hasAccuracy()) accuracy.toDouble() else null,
    vAccM = if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    speedAccMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond.toDouble() else null,
    courseDeg = if (hasBearing()) bearing.toDouble() else null,
    courseAccDeg = if (hasBearingAccuracy()) bearingAccuracyDegrees.toDouble() else null,
    provider = fixProvider(provider, isMockFix()),
    ageMs = fixAgeMs(nowElapsedNs, elapsedRealtimeNanos),
)

private fun Location.isMockFix(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider

/** The provider of a fix from its Android provider name; a mock fix is [FixProvider.SIMULATED]. */
internal fun fixProvider(name: String?, mock: Boolean): FixProvider? = when {
    mock -> FixProvider.SIMULATED
    name == LocationManager.GPS_PROVIDER -> FixProvider.GNSS
    name == LocationManager.NETWORK_PROVIDER -> FixProvider.NETWORK
    name == FUSED -> FixProvider.FUSED
    else -> null
}

/** How old a fix was at delivery, in ms; null when the clocks disagree (a fix from the future). */
internal fun fixAgeMs(nowElapsedNs: Long, fixElapsedNs: Long): Long? =
    (nowElapsedNs - fixElapsedNs).takeIf { it >= 0 }?.let { it / NS_PER_MS }

/** The fused provider's name (`LocationManager.FUSED_PROVIDER`, a constant only from API 31). */
private const val FUSED = "fused"
private const val NS_PER_MS = 1_000_000L
