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
 * Where the trip list comes from. The app implements it over the interim trip index and, once DRIVE-62's `TripRepository`
 * and Drives presenter land, over processed trips (scores, Story, route). Newest first.
 */
interface TripListSource {
    val trips: Flow<List<RecordedTrip>>
}
