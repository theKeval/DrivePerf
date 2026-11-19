package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.TripRecompute
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [TripRepository] whose [delete] keeps history right (F-HIS-02 AC1 and AC3, pipeline-and-scoring.md §5.7, §7.4).
 * The trip's files go first, as in [LocalTripRepository]; only then, in one transaction, the trip leaves its route
 * (a record it held passes to the route's best remaining run) and its rows are deleted. Then every later trip is
 * recomputed, so baselines no longer count the deleted trip. Everything else is [inner]'s.
 */
public class RecomputingTripRepository(
    private val inner: TripRepository,
    private val db: DrivePerfDatabase,
    private val files: TripFileStore,
    private val io: CoroutineContext,
    private val clock: Clock,
    private val recompute: TripRecompute,
) : TripRepository by inner {
    override suspend fun delete(tripId: String): FileResult {
        val startedAt = withContext(io) { db.tripQueries.tripById(tripId).executeAsOneOrNull()?.started_at }
        val result = withContext(io) {
            files.deleteTrip(tripId).also {
                if (it == FileResult.OK) {
                    db.transaction {
                        db.removeFromRoute(tripId, clock.wallMs())
                        db.tripQueries.deleteTrip(tripId)
                    }
                }
            }
        }
        if (result == FileResult.OK && startedAt != null) {
            withContext(io) { db.recomputeQueries.firstFinishedFrom(startedAt).executeAsOneOrNull() }
                ?.let { recompute.fromTrip(it) }
        }
        return result
    }
}
