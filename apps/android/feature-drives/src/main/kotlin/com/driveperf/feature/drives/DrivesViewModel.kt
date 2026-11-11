package com.driveperf.feature.drives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One row of the trip list, already formatted. */
data class TripRowUi(val id: String, val start: String, val summary: String)

/** What the Drives tab and the All drives screen draw. */
data class DrivesUiState(
    val loaded: Boolean = false,
    val trips: List<TripRowUi> = emptyList(),
) {
    val isEmpty: Boolean get() = loaded && trips.isEmpty()
}

/**
 * State holder for the trip list (S-HOME-01 latest drives, S-HIS-01 all drives).
 *
 * The rows say "Recorded": scores, Stories and routes arrive with the shared core's pipeline, and the list says so
 * honestly instead of showing placeholder numbers.
 */
class DrivesViewModel(
    source: TripListSource,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private val formatting = TripFormatting(zone, locale, today)

    val state: StateFlow<DrivesUiState> = source.trips
        .map { trips ->
            DrivesUiState(
                loaded = true,
                trips = trips.map { trip ->
                    TripRowUi(
                        id = trip.id,
                        start = formatting.start(trip.startedWallMs),
                        summary = "${formatting.duration(trip.durationS)} · ${formatting.distance(trip.distanceM)}",
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DrivesUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
