package com.driveperf.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.data.repository.SettingsRepository
import com.driveperf.data.repository.TripRepository
import com.driveperf.domain.model.DistanceUnit
import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.TripScore
import com.driveperf.domain.model.TripSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripDetailUiState(
    val trip: TripSession? = null,
    val events: List<DrivingEvent> = emptyList(),
    val score: TripScore? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val isLoading: Boolean = true,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle["tripId"])

    val uiState: StateFlow<TripDetailUiState> = combine(
        tripRepository.observeTrip(tripId),
        tripRepository.observeEventsForTrip(tripId),
        tripRepository.observeScoreForTrip(tripId),
        settingsRepository.observeDistanceUnit(),
    ) { trip, events, score, distanceUnit ->
        TripDetailUiState(
            trip = trip,
            events = events,
            score = score,
            distanceUnit = distanceUnit,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripDetailUiState(),
    )

    fun markAsPassenger() {
        viewModelScope.launch {
            tripRepository.markAsPassenger(tripId)
        }
    }

    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            onDeleted()
        }
    }
}
