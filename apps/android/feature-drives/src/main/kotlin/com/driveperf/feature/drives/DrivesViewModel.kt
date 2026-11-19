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
 * The Drives tab, All drives, the trip sheet and the data-quality sheet (S-HOME-01, S-HIS-01, S-TRP-01, S-DQ-01): a thin
 * wrapper over the shared [DrivesPresenter] (ADR-002 section 4), which it owns on `viewModelScope`.
 *
 * @param presenter makes the presenter in the scope it is given (`CoreServices.drivesPresenter`).
 */
class DrivesViewModel(presenter: (CoroutineScope) -> DrivesPresenter) : ViewModel() {
    private val presenter = presenter(viewModelScope)

    val state: StateFlow<DrivesState> = this.presenter.state

    /** One-shot navigation: [DrivesEffect.OpenReplay] opens S-RPL-01. */
    val effects: Flow<DrivesEffect> = this.presenter.effects

    fun onAction(intent: DrivesIntent) {
        // The trip sheet is a modal over the list: it has already slid away when Replay opens, and stays closed after.
        if (intent is DrivesIntent.OpenReplay) presenter.dispatch(DrivesIntent.CloseDetail)
        presenter.dispatch(intent)
    }

    override fun onCleared() {
        presenter.close()
    }
}
