package com.driveperf.platform.location

import android.location.Location
import com.driveperf.domain.capture.LocationSample

/** Converts an Android [Location] into the sample contract. Android already reports m/s, metres and degrees. */
internal fun Location.toSample(): LocationSample = LocationSample(
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
)
