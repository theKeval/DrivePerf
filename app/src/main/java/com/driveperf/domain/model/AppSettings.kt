package com.driveperf.domain.model

data class AppSettings(
    val autoDetectEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
)
