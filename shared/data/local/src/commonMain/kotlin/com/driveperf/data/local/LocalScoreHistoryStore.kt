package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreChange
import com.driveperf.domain.score.ScoreHistoryStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [ScoreHistoryStore] on SQLDelight (DRIVE-131): the trips a recompute walks, read with one query per table, and its
 * changed scores, written to the `score` table in one transaction.
 */
public class LocalScoreHistoryStore(private val db: DrivePerfDatabase, private val io: CoroutineContext) : ScoreHistoryStore {
    private val q get() = db.recomputeQueries

    override suspend fun finishedFrom(startedAt: Long): List<ScoredTrip> = withContext(io) {
        db.transactionWithResult {
            val scores = q.scoresFrom(startedAt).executeAsList().associateBy { it.trip_id }
            val routes = q.routeMatchesFrom(startedAt).executeAsList().associate { it.trip_id to it.route_id }
            val spans = q.segmentsFrom(startedAt).executeAsList().groupBy { it.trip_id }
            q.finishedFrom(startedAt).executeAsList().map { row ->
                ScoredTrip(row.toDomain(spans[row.id].orEmpty().map { it.toDomain() }), scores[row.id]?.toDomain(), routes[row.id])
            }
        }
    }

    /**
     * Compare-and-set per score: a change is written only while the stored score still equals what the walk read, so
     * feedback or a deletion committed during the walk is never overwritten.
     */
    override suspend fun saveScores(changes: List<ScoreChange>): List<Score> = withContext(io) {
        db.transactionWithResult {
            changes.mapNotNull { (before, after) ->
                val stored = db.tripQueries.scoreForTrip(after.tripId).executeAsOneOrNull()?.toDomain()
                if (stored != before) return@mapNotNull null
                db.tripQueries.upsertScore(after.toRow())
                after
            }
        }
    }
}
