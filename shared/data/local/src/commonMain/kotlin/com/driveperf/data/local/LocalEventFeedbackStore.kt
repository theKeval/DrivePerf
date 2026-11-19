package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.feedback.EventFeedbackStore
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.TripRecompute
import com.driveperf.domain.trip.EventFeedback
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [EventFeedbackStore] on SQLDelight: the feedback row and the re-scored trip commit together. A new score then
 * changes the comparison of every later trip, so [recompute] runs from this trip (§5.7 "Corrections", F-SCO-05).
 */
public class LocalEventFeedbackStore(
    private val db: DrivePerfDatabase,
    private val io: CoroutineContext,
    private val recompute: TripRecompute = TripRecompute.NONE,
) : EventFeedbackStore {
    override suspend fun save(feedback: EventFeedback, score: Score?) {
        withContext(io) {
            db.transaction {
                db.tripQueries.upsertFeedback(feedback.toRow())
                score?.let { db.tripQueries.upsertScore(it.toRow()) }
            }
        }
        if (score != null) recompute.fromTrip(feedback.tripId)
    }
}
