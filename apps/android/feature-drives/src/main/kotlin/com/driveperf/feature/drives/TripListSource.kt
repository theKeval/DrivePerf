package com.driveperf.feature.drives

import kotlinx.coroutines.flow.Flow

/** One recorded trip as the shell has it today: raw capture facts, not yet analysed. */
data class RecordedTrip(
    val id: String,
    val startedWallMs: Long,
    val durationS: Long,
    val distanceM: Double,
)

/**
 * Where the trip list comes from. The app implements it over the shared core's `TripRepository`; the Drives presenter
 * with scores, Story and route replaces it later. Newest first.
 */
interface TripListSource {
    val trips: Flow<List<RecordedTrip>>
}
