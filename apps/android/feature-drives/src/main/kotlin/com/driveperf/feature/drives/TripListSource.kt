package com.driveperf.feature.drives

import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import kotlinx.coroutines.flow.Flow

/** How a listed trip ended (F-CAP-06): complete, cut short by the OS, or a manual trip too short to score (§1.6). */
enum class RecordedStatus { FINALISED, INTERRUPTED, SHORT }

/**
 * One recorded trip as the Drives screens need it. [quality] is null until the core has analysed the trip; [question]
 * is the open "Was this a bus or train?" (F-CAP-04); [correction] is what the user said the trip was.
 */
data class RecordedTrip(
    val id: String,
    val startedWallMs: Long,
    val durationS: Long,
    val distanceM: Double,
    val status: RecordedStatus = RecordedStatus.FINALISED,
    val endedWallMs: Long? = null,
    val quality: TripQuality? = null,
    val question: Suspect? = null,
    val kind: TripKind = TripKind.DRIVER,
    val correction: TripCorrection? = null,
    val correctedAtMs: Long? = null,
    val estimatedDistanceM: Double = 0.0,
    val movingS: Double? = null,
    val stopCount: Int? = null,
    val maxSpeedMps: Double? = null,
)

/**
 * Where the trip list comes from. The app implements it over the shared core's `TripRepository`; the Drives presenter
 * with scores, Story and route replaces it later. Newest first.
 */
interface TripListSource {
    val trips: Flow<List<RecordedTrip>>
}

/** Answers and corrections about what a trip was (F-CAP-04), over the core's `TripCorrections`. */
interface TripCorrector {
    /** Trips marked as not a drive, kept 30 days. Newest first. */
    val notDrives: Flow<List<RecordedTrip>>

    /** "Was this a bus or train?" Yes or No. */
    suspend fun answer(tripId: String, yes: Boolean)

    /** "What was this?"; [TripCorrection.DRIVER] restores a trip as a drive. */
    suspend fun correct(tripId: String, to: TripCorrection)
}
