package com.driveperf.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** What the capture toolbar and the in-drive screen draw. Immutable. */
sealed interface CaptureUiState {
    /** Fine location is not granted: Start is unavailable and the toolbar says why (EC-PERM, S-ONB-16 equivalent). */
    data object NeedsLocation : CaptureUiState

    data object Ready : CaptureUiState

    data class Recording(
        val elapsed: String,
        val distanceKm: String,
        val gps: GpsHealth,
        val confirmingStop: Boolean,
        /** "Started by you" or "Started automatically". */
        val manual: Boolean = true,
        /** The start time as a clock label, for the source line. */
        val startedAtLabel: String = "",
        /** The car's nickname. Null until the Garage exists (DRIVE-173); the screen then shows no chip. */
        val carName: String? = null,
        /** Under 500 m or 2 minutes: the End sheet says it is kept as a short trip, without a score (F-CAP-17). */
        val shortTrip: Boolean = false,
        val passengerStep: PassengerStep = PassengerStep.HIDDEN,
    ) : CaptureUiState

    data object Failed : CaptureUiState
}

/**
 * State holder for capture (UDF: events in, one immutable state out). Native view model, because capture surfaces are
 * platform flows (ADR-002 section 4); it only calls the [CaptureController].
 *
 * Besides the toolbar [state] it owns the in-drive gate ([cover]): S-DRV-01 while a driver trip records, S-DRV-05 for
 * content requested while locked ([requestContent]) until the phone has been parked for 60 s after the trip
 * (F-CAP-07.4), the passenger escape (S-DRV-03) and the S-DRV-04 screens after the trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // one function per user event
class CaptureViewModel(
    private val controller: CaptureController,
    private val nowWallMs: () -> Long,
    private val tickMs: Long = 1_000L,
    private val carName: () -> String? = { null },
    private val timeLabel: (Long) -> String = ::clockLabel,
    private val finishTimeoutMs: Long = FINISH_TIMEOUT_MS,
) : ViewModel() {
    /** How the trip that just ended came to end, which decides the screen and whether the lock holds on. */
    private enum class Intent { STOPPED_BY_USER, PASSENGER_KEPT, DISCARDED, ENDED_ITSELF }

    private data class Phase(val tripId: String?, val manual: Boolean, val intent: Intent, val result: DriveCover? = null)

    /** After a driver trip: calm since [calmSinceMs] (null while the phone still looks like it is moving). */
    private data class Hold(val endedAtMs: Long, val calmSinceMs: Long?)

    private data class Ui(
        val confirmingStop: Boolean = false,
        val passengerStep: PassengerStep = PassengerStep.HIDDEN,
        val held: ContentTarget? = null,
        val phase: Phase? = null,
        val hold: Hold? = null,
    )

    private val ui = MutableStateFlow(Ui())
    private val ticking = controller.snapshot.flatMapLatest { snapshot -> ticking(snapshot) }
    private var previous: CaptureSnapshot = CaptureSnapshot.Idle
    private var decodeJob: Job? = null
    private var holdJob: Job? = null
    private val mutableReleased = MutableSharedFlow<ContentTarget>(extraBufferCapacity = 1)

    /** Content that was held (or asked for while locked) and may open now. The navigation host collects it. */
    val released: SharedFlow<ContentTarget> = mutableReleased

    val state: StateFlow<CaptureUiState> = combine(ticking, controller.canRecord, ui) { snapshot, canRecord, current ->
        when (snapshot) {
            CaptureSnapshot.Idle -> if (canRecord) CaptureUiState.Ready else CaptureUiState.NeedsLocation
            CaptureSnapshot.Failed -> CaptureUiState.Failed
            is CaptureSnapshot.Recording -> recordingState(snapshot, current)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CaptureUiState.Ready)

    /** The cover over the whole app, or null when navigation is free. */
    val cover: StateFlow<DriveCover?> = combine(ticking, ui) { snapshot, current ->
        coverOf(snapshot, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch { controller.snapshot.collect(::onSnapshot) }
    }

    private fun recordingState(snapshot: CaptureSnapshot.Recording, current: Ui): CaptureUiState.Recording {
        val elapsedMs = nowWallMs() - snapshot.startedWallMs
        return CaptureUiState.Recording(
            elapsed = CaptureFormatting.elapsed(elapsedMs),
            distanceKm = CaptureFormatting.distanceKm(snapshot.distanceM),
            gps = snapshot.gps,
            confirmingStop = current.confirmingStop,
            manual = snapshot.isManual,
            startedAtLabel = timeLabel(snapshot.startedWallMs),
            carName = carName(),
            shortTrip = TripEnd.isShort(snapshot.distanceM, elapsedMs / MS_PER_S),
            passengerStep = current.passengerStep,
        )
    }

    private fun coverOf(snapshot: CaptureSnapshot, current: Ui): DriveCover? {
        val recording = snapshot as? CaptureSnapshot.Recording
        val phase = current.phase
        val phaseApplies = phase != null && (recording == null || recording.tripId == phase.tripId)
        return when {
            recording != null && !phaseApplies -> current.held?.let {
                DriveCover.Held(it.noun, current.passengerStep, canMarkPassenger = true)
            } ?: DriveCover.InDrive(recordingState(recording, current))
            current.held != null && current.hold != null ->
                DriveCover.Held(current.held.noun, PassengerStep.HIDDEN, canMarkPassenger = false)
            phase != null -> phase.result ?: DriveCover.Decoding(phase.tripId, phase.intent == Intent.PASSENGER_KEPT)
            else -> null
        }
    }

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

    // ---- trip lifecycle ----

    private fun onSnapshot(next: CaptureSnapshot) {
        val before = previous
        previous = next
        when {
            next is CaptureSnapshot.Recording -> if (before !is CaptureSnapshot.Recording) onTripStarted()
            before is CaptureSnapshot.Recording && next is CaptureSnapshot.Idle -> onTripEnded(before)
        }
    }

    private fun onTripStarted() {
        decodeJob?.cancel()
        holdJob?.cancel()
        ui.update { it.copy(phase = null, hold = null, confirmingStop = false, passengerStep = PassengerStep.HIDDEN) }
    }

    private fun onTripEnded(trip: CaptureSnapshot.Recording) {
        val phase = ui.value.phase ?: Phase(trip.tripId, trip.isManual, Intent.ENDED_ITSELF)
        ui.update { it.copy(phase = phase, confirmingStop = false, passengerStep = PassengerStep.HIDDEN) }
        if (phase.intent == Intent.ENDED_ITSELF || phase.intent == Intent.STOPPED_BY_USER) startHold()
        if (phase.intent != Intent.DISCARDED) decode(phase)
    }

    private fun decode(phase: Phase) {
        decodeJob?.cancel()
        decodeJob = viewModelScope.launch {
            val tripId = phase.tripId
            var trip: FinishedTrip? = null
            var waited = 0L
            while (tripId != null && trip == null && waited <= finishTimeoutMs) {
                trip = controller.finishedTrip(tripId)
                if (trip == null) {
                    delay(POLL_MS)
                    waited += POLL_MS
                }
            }
            val result = savedCover(phase, trip)
            ui.update {
                val current = it.phase
                if (current != null && current.tripId == phase.tripId && current.result == null) {
                    it.copy(phase = current.copy(result = result))
                } else {
                    it
                }
            }
        }
    }

    private fun savedCover(phase: Phase, trip: FinishedTrip?): DriveCover {
        val kind = when {
            phase.intent == Intent.PASSENGER_KEPT -> SavedKind.PASSENGER
            trip != null && TripEnd.isShort(trip.distanceM, trip.durationS) -> SavedKind.SHORT
            else -> SavedKind.DRIVE
        }
        // A short automatic trip the core discarded never appears in the list: there is nothing to link to.
        if (trip == null && !phase.manual && phase.intent == Intent.ENDED_ITSELF) return DriveCover.NotSaved
        return DriveCover.Saved(
            tripId = phase.tripId,
            distanceKm = trip?.let { CaptureFormatting.distanceKm(it.distanceM) },
            minutes = trip?.let { maxOf(1, ((it.durationS + HALF_MINUTE_S) / SECONDS_PER_MINUTE).toInt()) },
            kind = kind,
            autoEnded = trip != null && TripEnd.endedByTimeout(trip, phase.intent == Intent.STOPPED_BY_USER, nowWallMs()),
        )
    }

    // ---- content gate (F-CAP-07) ----

    /** True while a driver trip records, or the phone has not been parked for 60 s since one ended. */
    fun isLocked(): Boolean {
        val current = ui.value
        val recording = controller.snapshot.value is CaptureSnapshot.Recording
        val unlocked = current.phase?.intent.let { it == Intent.PASSENGER_KEPT || it == Intent.DISCARDED }
        return (recording && !unlocked) || current.hold != null
    }

    /**
     * A notification, deep link or widget asked to open [target]. While locked it is held behind S-DRV-05 and opens
     * through [released] after the phone has been parked for 60 s; otherwise it opens at once. Returns true when it may
     * open now.
     */
    fun requestContent(target: ContentTarget): Boolean {
        if (!isLocked()) {
            mutableReleased.tryEmit(target)
            return true
        }
        ui.update { it.copy(held = target) }
        return false
    }

    private fun startHold() {
        val now = nowWallMs()
        ui.update { it.copy(hold = Hold(endedAtMs = now, calmSinceMs = now)) }
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            while (true) {
                delay(tickMs)
                if (evaluateHold()) break
            }
        }
    }

    /** One look at the movement signal; true once the hold is over. */
    private suspend fun evaluateHold(): Boolean {
        val hold = ui.value.hold
        if (hold != null) {
            val moving = controller.isMoving(hold.endedAtMs)
            val now = nowWallMs()
            val calmSince = if (moving) null else hold.calmSinceMs ?: now
            // The cap is a safety valve: with no live activity signal the last reading can stay "automotive" forever.
            val calm = calmSince != null && now - calmSince >= CALM_MS
            if (calm || now - hold.endedAtMs >= HOLD_MAX_MS) {
                releaseHeld(clearHold = true)
                controller.onMovementWatchEnded()
            } else {
                ui.update { if (it.hold != null) it.copy(hold = hold.copy(calmSinceMs = calmSince)) else it }
            }
        }
        return ui.value.hold == null
    }

    private fun releaseHeld(clearHold: Boolean) {
        val held = ui.value.held
        ui.update { it.copy(held = null, hold = if (clearHold) null else it.hold) }
        held?.takeIf { it.reopensWhenReleased }?.let(mutableReleased::tryEmit)
    }

    // ---- events ----

    fun onStart() {
        // Without fine location nothing records: the toolbar offers the permission instead (EC-PERM).
        if (controller.canRecord.value) controller.start()
    }

    /** The first tap on Stop asks for confirmation (S-DRV-02): a stray tap must not end a drive. */
    fun onStopRequested() {
        ui.update { it.copy(confirmingStop = true) }
    }

    fun onStopDismissed() {
        ui.update { it.copy(confirmingStop = false) }
    }

    fun onStopConfirmed() {
        ui.update { it.copy(confirmingStop = false, phase = phaseFor(Intent.STOPPED_BY_USER)) }
        controller.stop()
    }

    /** "I'm a passenger" on S-DRV-01 or S-DRV-05 opens S-DRV-03 (F-CAP-08). */
    fun onPassengerRequested() {
        ui.update { it.copy(passengerStep = PassengerStep.CHOOSE) }
    }

    /** Cancel, the scrim, or "Keep recording" on the deletion confirmation. */
    fun onPassengerDismissed() {
        ui.update { it.copy(passengerStep = PassengerStep.HIDDEN) }
    }

    /** "Keep the trip as a passenger trip": saved without a score, and the content lock lifts. */
    fun onPassengerKept() {
        ui.update { it.copy(passengerStep = PassengerStep.HIDDEN, phase = phaseFor(Intent.PASSENGER_KEPT)) }
        releaseHeld(clearHold = false)
        controller.markPassenger(keep = true)
    }

    /** "Don't save this trip" asks a second time, because it deletes. */
    fun onPassengerDiscardRequested() {
        ui.update { it.copy(passengerStep = PassengerStep.CONFIRM_DISCARD) }
    }

    /** "Delete this trip": the core stops recording and deletes the samples. */
    fun onPassengerDiscardConfirmed() {
        ui.update {
            it.copy(passengerStep = PassengerStep.HIDDEN, phase = phaseFor(Intent.DISCARDED, DriveCover.NotSaved))
        }
        releaseHeld(clearHold = false)
        controller.markPassenger(keep = false)
    }

    private fun phaseFor(intent: Intent, result: DriveCover? = null): Phase {
        val recording = controller.snapshot.value as? CaptureSnapshot.Recording
        return Phase(recording?.tripId, recording?.isManual ?: true, intent, result)
    }

    /** "Back to Drives" on the saved or not-saved screen. */
    fun onCoverDismissed() {
        decodeJob?.cancel()
        ui.update { it.copy(phase = null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FINISH_TIMEOUT_MS = 3_000L
        const val POLL_MS = 250L
        const val CALM_MS = 60_000L
        const val HOLD_MAX_MS = 600_000L
        const val MS_PER_S = 1_000L
        const val SECONDS_PER_MINUTE = 60L
        const val HALF_MINUTE_S = 30L
    }
}

/** "5:31 PM", in the phone's locale and time zone. */
internal fun clockLabel(wallMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(wallMs))
