package com.driveperf.presentation.drives

import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripVehicle
import com.driveperf.domain.trip.TripVehicles
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.presentation.score.ScoreBadgeUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// S-TRP-02 Edit trip and S-TRP-03 Delete trip (DRIVE-131, F-HIS-02): what they draw, what the user does on them, and
// the work behind them. Copy is from docs/design/DRIVE-131 and prototypes/app/screens/wp-d.js.

/**
 * What S-TRP-02 and S-TRP-03 write through: the trip's car ([vehicles]) and its deletion ([trips], the core's
 * `RecomputingTripRepository`, which deletes the files, then the rows, then recomputes the later trips).
 */
public class TripEditPorts(public val vehicles: TripVehicles, public val trips: TripRepository)

/** The five answers of the S-TRP-02 type picker (F-HIS-02 AC1) and the correction each saves. */
public enum class TripEditType(internal val key: String, public val correction: TripCorrection) {
    DRIVER("driver", TripCorrection.DRIVER),
    PASSENGER("passenger", TripCorrection.PASSENGER),
    TRANSIT("transit", TripCorrection.TRANSIT),
    TAXI("taxi", TripCorrection.TAXI),
    OTHER("other", TripCorrection.OTHER),
    ;

    /** Moved to "Not drives" rather than kept in history. */
    public val isNotADrive: Boolean get() = correction.isNotADrive

    internal companion object {
        /** What [trip] is now. A bike ride or a walk is "Other (not driving)". */
        fun of(trip: Trip): TripEditType = when (trip.correction) {
            TripCorrection.DRIVER -> DRIVER
            TripCorrection.PASSENGER -> PASSENGER
            TripCorrection.TRANSIT -> TRANSIT
            TripCorrection.TAXI -> TAXI
            TripCorrection.BIKE, TripCorrection.WALK, TripCorrection.OTHER -> OTHER
            null -> when (trip.kind) {
                TripKind.DRIVER -> DRIVER
                TripKind.PASSENGER -> PASSENGER
                TripKind.TRANSIT -> TRANSIT
                TripKind.TAXI -> TAXI
                TripKind.NOT_DRIVING -> OTHER
            }
        }
    }
}

/** The page S-TRP-02 shows: the trip's fields, or one of the pickers pushed inside the sheet. */
public enum class TripEditPage { MAIN, TYPE, CAR }

/** One row of the type picker: "Passenger" with "Not scored. Stays in your history." */
public data class TripTypeOptionUi(
    val type: TripEditType,
    val title: String,
    val detail: String,
    val selected: Boolean,
    /** The saved type ("Current" tag). */
    val current: Boolean,
    /** "Passenger. Not scored. Stays in your history. Not selected." */
    val accessibilityLabel: String,
)

/** One row of the car picker: "GTI" with "2019 Volkswagen Golf". */
public data class CarOptionUi(
    val id: String,
    val name: String,
    val detail: String?,
    /** "Default" tag. */
    val isDefault: Boolean,
    val selected: Boolean,
    val current: Boolean,
    val accessibilityLabel: String,
)

/** S-TRP-02 Edit trip: a large sheet over trip detail, with the type and car pickers pushed inside it. */
public data class TripEditUi(
    val tripId: String,
    val page: TripEditPage,
    /** "Edit trip" */
    val title: String,
    /** The trip summary row: "The Commute", "Thursday morning · 5 Nov", "37.9 km · 30 min" and its score badge. */
    val tripTitle: String,
    val tripSubtitle: String,
    val tripStats: String,
    val score: ScoreBadgeUi?,
    /** The chosen type ("Passenger") and what it means ("Not scored"). */
    val type: TripEditType,
    val typeValue: String,
    val typeDetail: String,
    val types: List<TripTypeOptionUi>,
    /** On the type picker, while a type other than Driver is chosen: "Passenger: not a drive you made. …". */
    val typeBanner: String?,
    /** Under the type picker. */
    val typeFooter: String,
    /** The chosen car ("GTI") and its year, make and model; null values without a car. Hidden without [cars]. */
    val carValue: String?,
    val carDetail: String?,
    val cars: List<CarOptionUi>,
    /** Under the car picker: what moving the drive does, or where drives go by default. */
    val carNote: String,
    /** "Save to apply. …" once something differs from the saved trip. */
    val changedBanner: String?,
    /** Save does something; otherwise ✓ / Save just closes. */
    val changed: Boolean,
    /** "Fix a drive after the fact: …" */
    val caption: String,
    /** "Delete drive" */
    val deleteLabel: String,
) {
    /** The car row and picker are shown only when the user has a car. */
    val hasCars: Boolean get() = cars.isNotEmpty()
}

/** S-TRP-03 Delete trip confirmation: a real, irreversible deletion (F-HIS-02 AC3, F-PRV-04). */
public data class DeleteTripUi(
    val tripId: String,
    /** "Delete this drive?" */
    val title: String,
    val message: String,
    /** The drive: "The Commute" over "Thursday morning · 5 Nov". */
    val tripTitle: String,
    val tripSubtitle: String,
    val confirm: String,
    val cancel: String,
    /** The deletion is running: both buttons wait. */
    val deleting: Boolean,
    /** Why the last attempt failed; null otherwise. */
    val error: String?,
)

/** What the user does on S-TRP-02 and S-TRP-03 (sent as [DrivesIntent.Edit]). */
public sealed interface TripEditAction {
    /** "Edit" on trip detail opens S-TRP-02 for [tripId] on [page] (the Story chips open a picker directly). */
    public data class Open(val tripId: String, val page: TripEditPage) : TripEditAction

    /** A row or the back chevron inside the sheet. */
    public data class ShowPage(val page: TripEditPage) : TripEditAction

    public data class PickType(val type: TripEditType) : TripEditAction

    public data class PickCar(val vehicleId: String) : TripEditAction

    /** ✓ / Save: writes what changed, then [DrivesEffect.TripEdited]. */
    public data object Save : TripEditAction

    /** × or drag down: discards the changes. */
    public data object Close : TripEditAction

    /** "Undo" on the toast: puts back the type and car the last save changed. */
    public data object Undo : TripEditAction

    /** "Delete drive": opens S-TRP-03 for [tripId]. */
    public data class AskDelete(val tripId: String) : TripEditAction

    public data object CancelDelete : TripEditAction

    /** "Delete": deletes the trip, then [DrivesEffect.TripDeleted]. */
    public data object ConfirmDelete : TripEditAction
}

/**
 * The work behind S-TRP-02 and S-TRP-03, kept out of [DrivesPresenter] so the presenter only routes to it. [find]
 * looks a listed trip up; [onChange] asks the presenter to render again; [emit] and [track] are the presenter's.
 *
 * Analytics: `trip_corrected {to, source}`, `trip_car_changed {source}` and `trip_deleted {source}`, with fixed values
 * only.
 */
@Suppress("LongParameterList") // the presenter's hooks
internal class TripEditing(
    private val scope: CoroutineScope,
    private val corrections: TripCorrections?,
    private val ports: TripEditPorts?,
    private val find: (String) -> Trip?,
    private val onChange: () -> Unit,
    private val emit: (DrivesEffect) -> Unit,
    private val track: (String, Map<String, String>) -> Unit,
) {
    private data class Draft(
        val tripId: String,
        val page: TripEditPage,
        val type: TripEditType? = null,
        val carId: String? = null,
        val carChosen: Boolean = false,
    )

    private data class Deletion(val tripId: String, val source: String, val deleting: Boolean = false, val failed: Boolean = false)

    /** What the last save changed, to put back on "Undo". */
    private data class Undo(val tripId: String, val correction: TripCorrection?, val carId: String?, val carChanged: Boolean)

    private data class State(
        val vehicles: List<TripVehicle> = emptyList(),
        val draft: Draft? = null,
        val deletion: Deletion? = null,
        val undo: Undo? = null,
    )

    private val state = MutableStateFlow(State())

    /** Corrections and the car and delete ports are wired. */
    val canEdit: Boolean = corrections != null && ports != null

    /** The user's active cars, oldest first. */
    val vehicles: List<TripVehicle> get() = state.value.vehicles

    /** Follows the user's cars. */
    fun start() {
        ports?.let { p -> scope.launch { p.vehicles.observeVehicles().collect { cars -> change { it.copy(vehicles = cars) } } } }
    }

    fun dispatch(action: TripEditAction) {
        when (action) {
            is TripEditAction.Open -> if (canEdit && find(action.tripId) != null) {
                change { it.copy(draft = Draft(action.tripId, action.page), deletion = null) }
            }
            is TripEditAction.ShowPage -> change { s -> s.copy(draft = s.draft?.copy(page = action.page)) }
            is TripEditAction.PickType -> change { s -> s.copy(draft = s.draft?.copy(type = action.type)) }
            is TripEditAction.PickCar -> change { s -> s.copy(draft = s.draft?.copy(carId = action.vehicleId, carChosen = true)) }
            TripEditAction.Save -> save()
            TripEditAction.Close -> change { it.copy(draft = null) }
            TripEditAction.Undo -> undo()
            is TripEditAction.AskDelete -> if (canEdit && find(action.tripId) != null) {
                val source = if (state.value.draft?.tripId == action.tripId) "edit" else "detail"
                change { it.copy(deletion = Deletion(action.tripId, source)) }
            }
            TripEditAction.CancelDelete -> change { s -> s.copy(deletion = s.deletion?.takeIf { it.deleting }) }
            TripEditAction.ConfirmDelete -> delete()
        }
    }

    private fun save() {
        val draft = state.value.draft ?: return
        change { it.copy(draft = null) }
        val trip = find(draft.tripId) ?: return
        val from = TripEditType.of(trip)
        val toType = draft.type?.takeIf { it != from }
        val carChanged = draft.carChosen && draft.carId != trip.vehicleId
        if (toType == null && !carChanged) return
        val carName = vehicles.firstOrNull { it.id == draft.carId }?.name
        scope.launch {
            toType?.let { type ->
                corrections?.correct(trip.id, type.correction)
                track("trip_corrected", mapOf("to" to type.key, "source" to SOURCE))
            }
            val movedCar = carChanged && ports?.vehicles?.setVehicle(trip.id, draft.carId) == true
            if (movedCar) track("trip_car_changed", mapOf("source" to SOURCE))
            change {
                it.copy(
                    undo = Undo(trip.id, (trip.correction ?: from.correction).takeIf { toType != null }, trip.vehicleId, movedCar),
                )
            }
            emit(DrivesEffect.TripEdited(trip.id, TripEditCopy.savedMessage(toType, carName.takeIf { movedCar })))
        }
    }

    private fun undo() {
        val undo = state.value.undo ?: return
        change { it.copy(undo = null) }
        scope.launch {
            undo.correction?.let { corrections?.correct(undo.tripId, it) }
            if (undo.carChanged) ports?.vehicles?.setVehicle(undo.tripId, undo.carId)
        }
    }

    private fun delete() {
        val deletion = state.value.deletion?.takeUnless { it.deleting } ?: return
        val trips = ports?.trips ?: return
        change { it.copy(deletion = deletion.copy(deleting = true, failed = false)) }
        scope.launch {
            if (trips.delete(deletion.tripId) == FileResult.OK) {
                track("trip_deleted", mapOf("source" to deletion.source))
                change { s ->
                    s.copy(
                        deletion = null,
                        draft = s.draft?.takeUnless { it.tripId == deletion.tripId },
                        undo = s.undo?.takeUnless { it.tripId == deletion.tripId },
                    )
                }
                emit(DrivesEffect.TripDeleted(deletion.tripId, TripEditCopy.DELETED))
            } else {
                change { it.copy(deletion = deletion.copy(deleting = false, failed = true)) }
            }
        }
    }

    private fun change(change: (State) -> State) {
        state.update(change)
        onChange()
    }

    /** S-TRP-02 for the open draft, if its trip is still listed. */
    fun editUi(rows: TripRows): TripEditUi? {
        val s = state.value
        val draft = s.draft ?: return null
        val trip = find(draft.tripId) ?: return null
        return TripEditCopy.sheet(trip, rows.row(trip), draft.page, draft.type ?: TripEditType.of(trip),
            if (draft.carChosen) draft.carId else trip.vehicleId, s.vehicles)
    }

    /** S-TRP-03, while it is open and its trip is still listed. */
    fun deleteUi(rows: TripRows): DeleteTripUi? {
        val deletion = state.value.deletion ?: return null
        val row = find(deletion.tripId)?.let(rows::row) ?: return null
        return TripEditCopy.dialog(row, deletion.deleting, deletion.failed)
    }

    private companion object {
        /** The only entry point so far is trip detail's "Edit" (`source`: trip_detail | story | history). */
        const val SOURCE = "trip_detail"
    }
}

/** The words of S-TRP-02 and S-TRP-03. */
internal object TripEditCopy {
    const val DELETED = "Drive deleted"

    fun title(type: TripEditType): String = when (type) {
        TripEditType.DRIVER -> "Driver"
        TripEditType.PASSENGER -> "Passenger"
        TripEditType.TRANSIT -> "Bus or train"
        TripEditType.TAXI -> "Taxi or rideshare"
        TripEditType.OTHER -> "Other (not driving)"
    }

    fun detail(type: TripEditType): String = when (type) {
        TripEditType.DRIVER -> "Scored and counted toward your baseline, routes and recaps."
        TripEditType.PASSENGER, TripEditType.TAXI -> "Not scored. Stays in your history."
        TripEditType.TRANSIT, TripEditType.OTHER -> "Moved to “Not drives”. Kept 30 days, then deleted."
    }

    /** "Marked as passenger · scores updated", or "Moved to the GTI · stats updated" for a car alone. */
    fun savedMessage(type: TripEditType?, car: String?): String = when {
        type != null -> "Marked as ${savedWord(type)} · scores updated"
        car != null -> "Moved to the $car · stats updated"
        else -> "Stats updated"
    }

    private fun savedWord(type: TripEditType): String = when (type) {
        TripEditType.DRIVER -> "a drive"
        TripEditType.PASSENGER -> "passenger"
        TripEditType.TRANSIT -> "bus or train"
        TripEditType.TAXI -> "taxi or rideshare"
        TripEditType.OTHER -> "not a drive"
    }

    @Suppress("LongParameterList") // the sheet's inputs
    fun sheet(trip: Trip, row: TripRowUi, page: TripEditPage, type: TripEditType, carId: String?, cars: List<TripVehicle>): TripEditUi {
        val saved = TripEditType.of(trip)
        val car = cars.firstOrNull { it.id == carId }
        val changed = type != saved || carId != trip.vehicleId
        return TripEditUi(
            tripId = trip.id,
            page = page,
            title = "Edit trip",
            tripTitle = row.title,
            tripSubtitle = row.subtitle,
            tripStats = row.stats,
            score = row.score,
            type = type,
            typeValue = title(type),
            typeDetail = if (type == TripEditType.DRIVER) "Scored" else "Not scored",
            types = TripEditType.entries.map { option -> typeOption(option, option == type, option == saved) },
            typeBanner = if (type == TripEditType.DRIVER) null else
                "${title(type)}: not a drive you made. Your scores, baseline, route records and recaps recompute without it, " +
                    "within 2 seconds.",
            typeFooter = "Correcting a drive teaches DrivePerf what your drives look like. It never leaves your phone.",
            carValue = car?.name ?: if (cars.isEmpty()) null else "No car",
            carDetail = car?.description,
            cars = cars.map { carOption(it, it.id == carId, it.id == trip.vehicleId) },
            carNote = if (car != null && carId != trip.vehicleId) {
                "Moves this drive to the ${car.name}. Per-car stats and the Story tint update."
            } else {
                "Drives go to your default car unless you change them here or on the Story."
            },
            changedBanner = if (changed) "Save to apply. Scores, baseline, route records and recaps recompute within 2 seconds." else null,
            changed = changed,
            caption = "Fix a drive after the fact: you were a passenger, it was a bus, or it was the other car.",
            deleteLabel = "Delete drive",
        )
    }

    private fun typeOption(type: TripEditType, selected: Boolean, current: Boolean) = TripTypeOptionUi(
        type = type,
        title = title(type),
        detail = detail(type),
        selected = selected,
        current = current,
        accessibilityLabel = listOfNotNull(
            "${title(type)}.", detail(type), "Current type.".takeIf { current }, if (selected) "Selected." else "Not selected.",
        ).joinToString(" "),
    )

    private fun carOption(car: TripVehicle, selected: Boolean, current: Boolean) = CarOptionUi(
        id = car.id,
        name = car.name,
        detail = car.description,
        isDefault = car.isDefault,
        selected = selected,
        current = current,
        accessibilityLabel = listOfNotNull(
            "${car.name}.", car.description?.let { "$it." }, "Default car.".takeIf { car.isDefault },
            if (selected) "Selected." else "Not selected.",
        ).joinToString(" "),
    )

    fun dialog(row: TripRowUi, deleting: Boolean, failed: Boolean) = DeleteTripUi(
        tripId = row.id,
        title = "Delete this drive?",
        message = "Its route, score, Story and raw sensor data are removed from this phone. This can’t be undone.",
        tripTitle = row.title,
        tripSubtitle = row.subtitle,
        confirm = "Delete",
        cancel = "Cancel",
        deleting = deleting,
        error = if (failed) "Couldn’t delete this drive. Try again." else null,
    )
}
