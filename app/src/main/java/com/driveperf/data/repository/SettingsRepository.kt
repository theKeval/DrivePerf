package com.driveperf.data.repository

import com.driveperf.domain.model.AppSettings
import com.driveperf.domain.model.DistanceUnit
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    fun observeDistanceUnit(): Flow<DistanceUnit>
    suspend fun setAutoDetect(enabled: Boolean)
    suspend fun setCloudSync(enabled: Boolean)
    suspend fun setDistanceUnit(distanceUnit: DistanceUnit)
}
