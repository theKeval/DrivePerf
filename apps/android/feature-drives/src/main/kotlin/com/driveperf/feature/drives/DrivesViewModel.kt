package com.driveperf.feature.drives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.presentation.drives.DrivesEffect
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesPresenter
import com.driveperf.presentation.drives.DrivesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Drives tab, All drives, trip detail, the score screens and the data-quality sheet (S-HOME-01, S-HIS-01, S-TRP-01,
 * S-SCO-01, S-SCO-02, S-DQ-01): a thin wrapper over the shared [DrivesPresenter] (ADR-002 section 4), which it owns on
 * `viewModelScope`. Each destination has its own, so trip detail keeps its trip while Replay or Analysis is on top.
 *
 * @param presenter makes the presenter in the scope it is given (`CoreServices.drivesPresenter`).
 */
class DrivesViewModel(presenter: (CoroutineScope) -> DrivesPresenter) : ViewModel() {
    private val presenter = presenter(viewModelScope)

    val state: StateFlow<DrivesState> = this.presenter.state

    /** One-shot navigation: trip detail, the score screens, Replay and Analysis (`DrivesNavigation.kt`). */
    val effects: Flow<DrivesEffect> = this.presenter.effects

    fun onAction(intent: DrivesIntent) {
        presenter.dispatch(intent)
    }

    override fun onCleared() {
        presenter.close()
    }
}
