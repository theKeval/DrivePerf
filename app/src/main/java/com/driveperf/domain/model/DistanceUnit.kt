package com.driveperf.domain.model

enum class DistanceUnit(
    val distanceSuffix: String,
    val speedSuffix: String,
) {
    KILOMETERS(distanceSuffix = "km", speedSuffix = "km/h"),
    MILES(distanceSuffix = "mi", speedSuffix = "mph"),
}
