package com.driveperf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.domain.settings.SettingsVehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/** Settings writes are serialised so quick taps never replace a newer choice with an older snapshot. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val state: StateFlow<Settings> = repository.state
    val cars = MutableStateFlow<List<SettingsVehicle>>(emptyList())
    val saveFailed = MutableStateFlow(false)
    private val writes = Mutex()

    init { viewModelScope.launch { cars.value = repository.vehicles() } }

    fun change(update: (Settings) -> Settings) {
        viewModelScope.launch {
            writes.withLock { saveFailed.value = !repository.update(update(state.value)) }
        }
    }
}
