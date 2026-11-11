package com.driveperf.app.bridge

import com.driveperf.feature.drives.RecordedTrip
import com.driveperf.feature.drives.TripListSource
import com.driveperf.platform.capture.TripIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Feeds the trip list from the interim trip index. INTERIM (DRIVE-72): replaced by DRIVE-62's `TripRepository`. */
class PlatformTripListSource(index: TripIndex) : TripListSource {
    override val trips: Flow<List<RecordedTrip>> = index.entries.map { entries ->
        entries.map { RecordedTrip(it.tripId, it.startedWallMs, it.durationS, it.distanceM) }
    }
}
