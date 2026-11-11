package com.driveperf.app.bridge

import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.openQuestion
import com.driveperf.feature.drives.RecordedStatus
import com.driveperf.feature.drives.RecordedTrip
import com.driveperf.feature.drives.TripCorrector
import com.driveperf.feature.drives.TripListSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Feeds the trip list from the shared core's `TripRepository` (finished and interrupted trips, newest first) and
 * the "Not drives" filter, answers and corrections from its `TripCorrections` (DRIVE-100).
 */
class PlatformTripListSource(trips: TripRepository, private val corrections: TripCorrections) : TripListSource, TripCorrector {
    override val trips: Flow<List<RecordedTrip>> = trips.observeTrips().map { list -> list.map(::recorded) }

    override val notDrives: Flow<List<RecordedTrip>> = corrections.observeNotDrives().map { list -> list.map(::recorded) }

    override suspend fun answer(tripId: String, yes: Boolean) {
        corrections.answerSuspect(tripId, if (yes) SuspectAnswer.YES else SuspectAnswer.NO)
    }

    override suspend fun correct(tripId: String, to: TripCorrection) {
        corrections.correct(tripId, to)
    }

    internal companion object {
        private const val SHORT_TRIP_M = 500.0
        private const val SHORT_TRIP_S = 120.0

        /** A core trip as the Drives screens show it. A manual trip under 500 m or 120 s is "Short trip" (§1.6). */
        fun recorded(t: Trip): RecordedTrip {
            val distance = t.distanceM ?: 0.0
            val duration = t.durationS ?: 0.0
            val status = when {
                t.status == TripStatus.INTERRUPTED -> RecordedStatus.INTERRUPTED
                t.source == TripSource.MANUAL && (distance < SHORT_TRIP_M || duration < SHORT_TRIP_S) -> RecordedStatus.SHORT
                else -> RecordedStatus.FINALISED
            }
            return RecordedTrip(
                id = t.id,
                startedWallMs = t.startedAt,
                durationS = duration.toLong(),
                distanceM = distance,
                status = status,
                endedWallMs = t.endedAt,
                quality = t.quality,
                question = t.openQuestion,
                kind = t.kind,
                correction = t.correction,
                correctedAtMs = t.correctedAt,
                estimatedDistanceM = t.estimatedDistanceM ?: 0.0,
                movingS = t.movingS,
                stopCount = t.stopCount,
                maxSpeedMps = t.maxSpeedMps,
            )
        }
    }
}
