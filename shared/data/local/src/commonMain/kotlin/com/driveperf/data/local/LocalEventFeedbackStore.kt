package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.feedback.EventFeedbackStore
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.EventFeedback
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [EventFeedbackStore] on SQLDelight: the feedback row and the re-scored trip commit together. */
public class LocalEventFeedbackStore(private val db: DrivePerfDatabase, private val io: CoroutineContext) : EventFeedbackStore {
    override suspend fun save(feedback: EventFeedback, score: Score?): Unit = withContext(io) {
        db.transaction {
            db.tripQueries.upsertFeedback(feedback.toRow())
            score?.let { db.tripQueries.upsertScore(it.toRow()) }
        }
    }
}
