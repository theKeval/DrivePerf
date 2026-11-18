package com.driveperf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.domain.settings.SettingsVehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/** Settings writes are serialised so quick taps never replace a newer choice with an older snapshot. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val state: StateFlow<Settings> = repository.state
    private val mutableCars = MutableStateFlow<List<SettingsVehicle>>(emptyList())
    val cars: StateFlow<List<SettingsVehicle>> = mutableCars.asStateFlow()
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = mutableSaveFailed.asStateFlow()
    private val writes = Mutex()

    init { viewModelScope.launch { mutableCars.value = repository.vehicles() } }

    fun dismissError() { mutableSaveFailed.value = false }

    fun change(update: (Settings) -> Settings) {
        viewModelScope.launch {
            writes.withLock { mutableSaveFailed.value = !repository.update(update(state.value)) }
        }
    }
}
