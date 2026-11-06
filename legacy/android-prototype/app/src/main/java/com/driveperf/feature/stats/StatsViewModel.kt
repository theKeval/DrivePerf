package com.driveperf.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.data.repository.SettingsRepository
import com.driveperf.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(
    tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val clock: Clock = Clock.systemDefaultZone()
    private val zoneId: ZoneId = clock.zone

    val uiState: StateFlow<StatsUiState> = combine(
        tripRepository.observeAllTrips(),
        settingsRepository.observeDistanceUnit(),
    ) { trips, distanceUnit ->
        val snapshot = buildStatsSnapshot(
            trips = trips,
            now = clock.instant(),
            zoneId = zoneId,
        )
        StatsUiState(
            isLoading = false,
            distanceUnit = distanceUnit,
            hasAnyTrips = snapshot.hasAnyTrips,
            hasPartialData = snapshot.hasPartialData,
            summary = snapshot.summary,
            chartBars = snapshot.chartBars,
            longestTripRecord = snapshot.longestTripRecord,
            fastestTripRecord = snapshot.fastestTripRecord,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )
}
