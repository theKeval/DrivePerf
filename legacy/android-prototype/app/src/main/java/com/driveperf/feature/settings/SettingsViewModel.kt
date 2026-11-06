package com.driveperf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.data.repository.SettingsRepository
import com.driveperf.domain.model.DistanceUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoDetectEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.observeSettings()
        .map { settings ->
            SettingsUiState(
                autoDetectEnabled = settings.autoDetectEnabled,
                cloudSyncEnabled = settings.cloudSyncEnabled,
                distanceUnit = settings.distanceUnit,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setAutoDetect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDetect(enabled)
        }
    }

    fun setCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCloudSync(enabled)
        }
    }

    fun setDistanceUnit(distanceUnit: DistanceUnit) {
        viewModelScope.launch {
            settingsRepository.setDistanceUnit(distanceUnit)
        }
    }
}
