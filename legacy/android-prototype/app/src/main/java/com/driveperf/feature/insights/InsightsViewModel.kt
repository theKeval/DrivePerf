package com.driveperf.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class InsightsUiState(
    val totalTrips: Int = 0,
    val avgSafetyScore: Float? = null,
    val avgQualityScore: Float? = null,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    tripRepository: TripRepository,
) : ViewModel() {

    private val thirtyDaysAgoMs: Long
        get() = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

    val uiState: StateFlow<InsightsUiState> = combine(
        tripRepository.observeCompletedTripCount(),
        tripRepository.observeAverageSafetyScore(thirtyDaysAgoMs),
        tripRepository.observeAverageQualityScore(thirtyDaysAgoMs),
    ) { count, avgSafety, avgQuality ->
        InsightsUiState(
            totalTrips = count,
            avgSafetyScore = avgSafety,
            avgQualityScore = avgQuality,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InsightsUiState(),
    )
}
