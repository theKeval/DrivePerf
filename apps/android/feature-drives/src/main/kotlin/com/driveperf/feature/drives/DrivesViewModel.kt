package com.driveperf.feature.drives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One row of the trip list, already formatted. */
data class TripRowUi(
    val id: String,
    val start: String,
    val summary: String,
    /** The compact data-quality badge (components.md §10.1); null before analysis. */
    val quality: QualityBadge? = null,
    /** "Interrupted" tag (F-CAP-06). */
    val interrupted: Boolean = false,
    /** "Short trip · not scored" (§1.6). */
    val short: Boolean = false,
    /** The open "Was this a bus or train?" question (F-CAP-04). */
    val question: Suspect? = null,
    /** "Passenger", "Bus or train"…: the card is dimmed and doesn't count. */
    val kindLabel: String? = null,
)

/** Which list All drives shows (S-HIS-01 filter). */
enum class DrivesFilter { DRIVES, NOT_DRIVES }

/** What the Drives tab and the All drives screen draw. */
data class DrivesUiState(
    val loaded: Boolean = false,
    val trips: List<TripRowUi> = emptyList(),
    val notDrives: List<TripRowUi> = emptyList(),
    val filter: DrivesFilter = DrivesFilter.DRIVES,
    /** Answers and corrections are possible (the core's corrections port is wired). */
    val canCorrect: Boolean = false,
    /** The open trip sheet (S-TRP-01 subset), if any. */
    val detail: TripDetailUi? = null,
    /** The open data-quality sheet (S-DQ-01), if any. */
    val quality: QualitySheetUi? = null,
) {
    val isEmpty: Boolean get() = loaded && trips.isEmpty()

    /** The rows of the current filter. */
    val shown: List<TripRowUi> get() = if (filter == DrivesFilter.DRIVES) trips else notDrives
}

/** What the user did on a Drives screen. */
sealed interface DrivesAction {
    data class Open(val tripId: String) : DrivesAction
    data class OpenQuality(val tripId: String) : DrivesAction
    data object CloseQuality : DrivesAction
    data object CloseDetail : DrivesAction
    data class Answer(val tripId: String, val yes: Boolean) : DrivesAction
    data class Correct(val tripId: String, val to: TripCorrection) : DrivesAction
    data class Filter(val filter: DrivesFilter) : DrivesAction
}

/**
 * State holder for the trip list (S-HOME-01 latest drives, S-HIS-01 all drives), the trip sheet and the data-quality
 * sheet. [corrector] is null where answers and corrections are not wired.
 */
class DrivesViewModel(
    source: TripListSource,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    today: () -> LocalDate = { LocalDate.now(zone) },
    private val corrector: TripCorrector? = null,
) : ViewModel() {
    private val formatting = TripFormatting(zone, locale, today)
    private val presenter = TripPresenter(zone, locale)
    private val selection = MutableStateFlow(Selection())

    private data class Selection(
        val detailId: String? = null,
        val qualityId: String? = null,
        val filter: DrivesFilter = DrivesFilter.DRIVES,
    )

    val state: StateFlow<DrivesUiState> = combine(
        source.trips,
        corrector?.notDrives ?: flowOf(emptyList()),
        selection,
    ) { trips, notDrives, sel ->
        val byId = (trips + notDrives).associateBy { it.id }
        DrivesUiState(
            loaded = true,
            trips = trips.map(::row),
            notDrives = notDrives.map(::row),
            filter = sel.filter,
            canCorrect = corrector != null,
            detail = sel.detailId?.let(byId::get)?.let { presenter.detail(it, formatting.start(it.startedWallMs)) },
            quality = sel.qualityId?.let(byId::get)?.let(presenter::quality),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DrivesUiState())

    fun onAction(action: DrivesAction) {
        when (action) {
            is DrivesAction.Open -> selection.value = selection.value.copy(detailId = action.tripId)
            is DrivesAction.OpenQuality -> selection.value = selection.value.copy(qualityId = action.tripId)
            DrivesAction.CloseQuality -> selection.value = selection.value.copy(qualityId = null)
            DrivesAction.CloseDetail -> selection.value = selection.value.copy(detailId = null, qualityId = null)
            is DrivesAction.Filter -> selection.value = selection.value.copy(filter = action.filter)
            is DrivesAction.Answer -> corrector?.let { c -> viewModelScope.launch { c.answer(action.tripId, action.yes) } }
            is DrivesAction.Correct -> corrector?.let { c -> viewModelScope.launch { c.correct(action.tripId, action.to) } }
        }
    }

    private fun row(trip: RecordedTrip) = TripRowUi(
        id = trip.id,
        start = formatting.start(trip.startedWallMs),
        summary = "${formatting.duration(trip.durationS)} · ${formatting.distance(trip.distanceM)}",
        quality = trip.quality?.badge,
        interrupted = trip.status == RecordedStatus.INTERRUPTED,
        short = trip.status == RecordedStatus.SHORT,
        question = trip.question,
        kindLabel = TripPresenter.kindLabel(trip),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
