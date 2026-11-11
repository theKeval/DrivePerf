package com.driveperf.app.bridge

import com.driveperf.domain.repository.TripRepository
import com.driveperf.feature.drives.RecordedTrip
import com.driveperf.feature.drives.TripListSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Feeds the trip list from the shared core's `TripRepository` (finished and interrupted trips, newest first). */
class PlatformTripListSource(trips: TripRepository) : TripListSource {
    override val trips: Flow<List<RecordedTrip>> = trips.observeTrips().map { list ->
        list.map { RecordedTrip(it.id, it.startedAt, (it.durationS ?: 0.0).toLong(), it.distanceM ?: 0.0) }
    }
}
