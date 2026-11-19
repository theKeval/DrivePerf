package com.driveperf.pipeline.score

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreChange
import com.driveperf.domain.score.ScoreHistoryStore
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.testing.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.assertNotNull

/**
 * Trips, their scores and events in memory, with every port [HistoryRecompute] uses over them. The store writes a
 * change only while the stored score still equals what the walk read, as `LocalScoreHistoryStore` does. [holdSave]
 * pauses the next save until it is completed, so tests can change the history in the middle of a walk.
 */
internal class RecomputeWorld(val clock: TestClock = TestClock()) {
    data class Scored(val score: Score, val events: List<DrivingEvent>)

    val trips = linkedMapOf<String, ScoredTrip>()
    val events = mutableMapOf<String, List<DrivingEvent>>()
    val feedback = mutableMapOf<String, Map<String, EventFeedback>>()
    var saves = 0
    var reads = 0
    var historyPages = 0
    var holdSave: CompletableDeferred<Unit>? = null

    /** Set when a walk reaches its save while [holdSave] is set. */
    val savePending = CompletableDeferred<Unit>()

    fun add(id: String, startedAt: Long, from: Scored, routeId: String? = null, context: DriveContext = DriveContext.CITY) {
        trips[id] = ScoredTrip(trip(id, startedAt, context = context), from.score.copy(tripId = id), routeId)
        events[id] = from.events
    }

    fun score(id: String): Score = assertNotNull(trips.getValue(id).score)

    fun setKind(id: String, kind: TripKind) {
        val t = trips.getValue(id)
        trips[id] = t.copy(trip = t.trip.copy(kind = kind))
    }

    val store = object : ScoreHistoryStore {
        override suspend fun finishedFrom(startedAt: Long): List<ScoredTrip> {
            reads++
            return trips.values
                .filter { it.trip.startedAt >= startedAt }
                .sortedWith(compareBy<ScoredTrip> { it.trip.startedAt }.thenBy { it.trip.id })
        }

        override suspend fun saveScores(changes: List<ScoreChange>): List<Score> {
            holdSave?.let { gate ->
                holdSave = null
                savePending.complete(Unit)
                gate.await()
            }
            saves++
            return changes.mapNotNull { (before, after) ->
                val stored = trips[after.tripId]
                if (stored?.score != before) return@mapNotNull null
                trips[after.tripId] = stored.copy(score = after)
                after
            }
        }
    }

    val history = object : TripHistory {
        override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int): List<ScoredTrip> {
            historyPages++
            return trips.values
                .filter { it.trip.startedAt < beforeStartedAt }
                .sortedWith(compareByDescending<ScoredTrip> { it.trip.startedAt }.thenByDescending { it.trip.id })
                .take(limit)
        }
    }

    val repository = object : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = emptyFlow()

        override suspend fun trip(id: String): Trip? = trips[id]?.trip

        override suspend fun events(tripId: String): List<DrivingEvent> = events[tripId].orEmpty()

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback[tripId].orEmpty()

        override suspend fun score(tripId: String): Score? = trips[tripId]?.score

        override suspend fun delete(tripId: String): FileResult = FileResult.OK
    }

    val previews = object : PreviewStore {
        override suspend fun write(tripId: String, rows: List<PreviewRow>) = true

        override suspend fun read(tripId: String) = listOf(PreviewRow(0.0, 2.5, -27.5, 10.0, null, null, null, 0))
    }

    val recompute = HistoryRecompute(history, store, repository, clock, previews)

    /** 2,000 drives over three contexts, every seventh on one of five routes, walked once. */
    suspend fun longHistory(): RecomputeWorld = apply {
        val contexts = DriveContext.entries
        (0 until LONG).forEach { i ->
            val from = listOf(g02, g03, g04)[i % 3]
            add("t$i", i * 1_000L, from, routeId = if (i % 7 == 0) "route-${i % 5}" else null, context = contexts[i % 3])
        }
        recompute.from(0L)
    }

    companion object {
        const val LONG = 2_000

        /** Scored drives from the §8.3 goldens: G-02 is 97, G-03 is 88, G-04 is 52 (events on every channel). */
        val g02: Scored by lazy { scored(Goldens.g02) }
        val g03: Scored by lazy { scored(Goldens.g03) }
        val g04: Scored by lazy { scored(Goldens.g04) }

        private fun scored(trace: R1Trace): Scored {
            val out = TripScorer().score(
                R1Analyser().analyse(trace),
                ScoringContext(
                    "x", true, baselineEligible = true, distanceM = 5_000.0, routeId = null, priors = emptyList(), nowWallMs = 1L,
                ),
            )
            return Scored(assertNotNull(out.score), out.events)
        }

        fun trip(id: String, startedAt: Long, kind: TripKind = TripKind.DRIVER, context: DriveContext = DriveContext.CITY) = Trip(
            id = id, status = TripStatus.FINALISED, kind = kind, source = TripSource.AUTO, startedAt = startedAt,
            startTzOffsetMin = 0, context = context, distanceM = 5_000.0,
            quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, null, null, 600.0, 0.0, 600.0, emptyList()),
            createdAt = startedAt, updatedAt = startedAt,
        )
    }
}
