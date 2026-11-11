package com.driveperf.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** What the capture toolbar and the in-drive screen draw. Immutable. */
sealed interface CaptureUiState {
    /** Fine location is not granted: Start is unavailable and the toolbar says why. */
    data object NeedsLocation : CaptureUiState

    data object Ready : CaptureUiState

    data class Recording(
        val elapsed: String,
        val distanceKm: String,
        val gps: GpsHealth,
        val confirmingStop: Boolean,
    ) : CaptureUiState

    data object Failed : CaptureUiState
}

/**
 * State holder for capture (UDF: events in, one immutable state out). Native view model, because capture surfaces are
 * platform flows (ADR-002 section 4); it only calls the [CaptureController].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModel(
    private val controller: CaptureController,
    private val nowWallMs: () -> Long,
    private val tickMs: Long = 1_000L,
) : ViewModel() {
    private val confirmingStop = MutableStateFlow(false)

    val state: StateFlow<CaptureUiState> = combine(
        controller.snapshot.flatMapLatest { snapshot -> ticking(snapshot) },
        controller.canRecord,
        confirmingStop,
    ) { snapshot, canRecord, confirming ->
        when (snapshot) {
            CaptureSnapshot.Idle -> if (canRecord) CaptureUiState.Ready else CaptureUiState.NeedsLocation
            CaptureSnapshot.Failed -> CaptureUiState.Failed
            is CaptureSnapshot.Recording -> CaptureUiState.Recording(
                elapsed = CaptureFormatting.elapsed(nowWallMs() - snapshot.startedWallMs),
                distanceKm = CaptureFormatting.distanceKm(snapshot.distanceM),
                gps = snapshot.gps,
                confirmingStop = confirming,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CaptureUiState.Ready)

    /** Re-emits the snapshot every [tickMs] while recording so the elapsed time advances without new fixes. */
    private fun ticking(snapshot: CaptureSnapshot) = flow {
        emit(snapshot)
        if (snapshot is CaptureSnapshot.Recording) {
            while (true) {
                delay(tickMs)
                emit(snapshot)
            }
        }
    }

    fun onStart() {
        controller.start()
    }

    /** The first tap on Stop asks for confirmation (S-DRV-02): a stray tap must not end a drive. */
    fun onStopRequested() {
        confirmingStop.value = true
    }

    fun onStopDismissed() {
        confirmingStop.value = false
    }

    fun onStopConfirmed() {
        confirmingStop.value = false
        controller.stop()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
