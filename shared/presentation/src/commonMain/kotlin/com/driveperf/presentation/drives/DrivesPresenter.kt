package com.driveperf.presentation.drives

import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.foundation.Clock
import com.driveperf.presentation.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Drives tab, All drives, the trip sheet and the data-quality sheet (S-HOME-01, S-HIS-01, S-TRP-01, S-DQ-01;
 * ADR-002 section 4). It owns the trip and Not drives lists, the filter, which sheet is open, answers and corrections
 * (F-CAP-04), and the hand-off to Replay ([DrivesEffect.OpenReplay]). Both apps draw from [state]; only dates and times
 * are formatted natively ([DrivesFormatter]).
 *
 * An open sheet follows its trip: it updates when the trip or the units change, and closes itself when the trip is gone.
 *
 * @param trips finished and interrupted trips, newest first (`TripRepository.observeTrips`).
 * @param corrections answers and corrections; null where they are not wired, and then nothing can be corrected.
 * @param units the user's unit system, from the shared settings.
 * @param clock its wall time decides "This week" and "Last week".
 */
@Suppress("LongParameterList") // the screen's inputs, injected so the state machine stays testable
public class DrivesPresenter(
    trips: Flow<List<Trip>>,
    private val corrections: TripCorrections?,
    units: Flow<UnitSystem>,
    private val format: DrivesFormatter,
    private val clock: Clock,
    scope: CoroutineScope,
) : BasePresenter<DrivesState, DrivesIntent, DrivesEffect>(DrivesState(canCorrect = corrections != null), scope) {
    /** What the state is built from. [trips] is null until the first list arrives. */
    private data class Inputs(
        val trips: List<Trip>? = null,
        val notDrives: List<Trip> = emptyList(),
        val units: UnitSystem = UnitSystem.METRIC,
        val detailId: String? = null,
        val qualityId: String? = null,
        /** The user's choice; null until they choose. */
        val filter: DrivesFilter? = null,
    )

    private val inputs = MutableStateFlow(Inputs())

    /** The scope [onMain] made for this presenter alone; [close] cancels it too. */
    private var ownedScope: CoroutineScope? = null

    init {
        this.scope.launch { trips.collect { list -> change { it.copy(trips = list) } } }
        corrections?.let { port ->
            this.scope.launch { port.observeNotDrives().collect { list -> change { it.copy(notDrives = list) } } }
        }
        this.scope.launch { units.collect { system -> change { it.copy(units = system) } } }
    }

    override fun close() {
        super.close()
        ownedScope?.cancel()
    }

    override fun dispatch(intent: DrivesIntent) {
        when (intent) {
            is DrivesIntent.Open -> change { it.copy(detailId = intent.tripId) }
            DrivesIntent.CloseDetail -> change { it.copy(detailId = null, qualityId = null) }
            is DrivesIntent.OpenQuality -> change { it.copy(qualityId = intent.tripId) }
            DrivesIntent.CloseQuality -> change { it.copy(qualityId = null) }
            is DrivesIntent.Filter -> change { it.copy(filter = intent.filter) }
            is DrivesIntent.OpenReplay -> openReplay(intent.tripId)
            is DrivesIntent.Answer -> corrections?.let { port ->
                scope.launch { port.answerSuspect(intent.tripId, if (intent.yes) SuspectAnswer.YES else SuspectAnswer.NO) }
            }
            is DrivesIntent.Correct -> corrections?.let { port -> scope.launch { port.correct(intent.tripId, intent.to) } }
        }
    }

    /** Replay keeps the trip sheet under it (iOS pushes Replay over it; Android closes its sheet first). */
    private fun openReplay(tripId: String) {
        val i = inputs.value
        val trip = (i.trips.orEmpty() + i.notDrives).firstOrNull { it.id == tripId } ?: return
        change { it.copy(qualityId = null) }
        val rows = TripRows(i.units, format, clock.wallMs())
        emit(DrivesEffect.OpenReplay(tripId, rows.title(trip), "Map of your drive, ${distanceText(trip.distance, i.units)}."))
    }

    /** Applies [change] to the inputs, then rebuilds the state from the newest inputs. */
    private fun change(change: (Inputs) -> Inputs) {
        inputs.update(change)
        update { render(inputs.value) }
    }

    private fun render(i: Inputs): DrivesState {
        val rows = TripRows(i.units, format, clock.wallMs())
        val trips = i.trips.orEmpty().sortedByDescending { it.startedAt }
        val notDrives = i.notDrives.sortedByDescending { it.startedAt }
        val tripRows = trips.map(rows::row)
        val byId = (trips + notDrives).associateBy { it.id }
        return DrivesState(
            loaded = i.trips != null,
            trips = tripRows,
            notDrives = notDrives.map(rows::row),
            sections = rows.sections(trips, tripRows),
            // With no drives left, All drives opens on the trips marked as not a drive.
            filter = i.filter ?: if (trips.isEmpty() && notDrives.isNotEmpty()) DrivesFilter.NOT_DRIVES else DrivesFilter.DRIVES,
            canCorrect = corrections != null,
            detail = i.detailId?.let(byId::get)?.let { TripSheet(i.units, format).detail(it, rows.title(it), rows.subtitle(it)) },
            quality = i.qualityId?.let(byId::get)?.quality?.let(QualitySheet(i.units)::sheet),
            countText = drivesCount(trips.size),
            sinceText = trips.lastOrNull()?.let(rows::since),
        )
    }

    public companion object {
        /**
         * A presenter on the main thread in a scope of its own, for Swift, which cannot make a `CoroutineScope`: the
         * iOS UI-test fixture and previews pass fixed [trips]. The app itself uses `CoreServices.drivesPresenter`.
         * Call [close] on deinit; it cancels the scope.
         */
        public fun onMain(trips: List<Trip>, units: UnitSystem, format: DrivesFormatter, clock: Clock): DrivesPresenter =
            onMain(flowOf(trips), null, flowOf(units), format, clock)

        /** [DrivesPresenter] on the main thread in a scope of its own (`CoreServices.drivesPresenter`). Call [close] when done. */
        public fun onMain(
            trips: Flow<List<Trip>>,
            corrections: TripCorrections?,
            units: Flow<UnitSystem>,
            format: DrivesFormatter,
            clock: Clock,
        ): DrivesPresenter {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            return DrivesPresenter(trips, corrections, units, format, clock, scope).also { it.ownedScope = scope }
        }
    }
}
