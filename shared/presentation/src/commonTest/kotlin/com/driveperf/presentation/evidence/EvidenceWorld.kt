package com.driveperf.presentation.evidence

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.feedback.EventFeedbackStore
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.feedback.ReportEventInaccurate
import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFileStore
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileRef
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A synthetic trip with three events for the evidence and tip tests; no real locations. */
internal class EvidenceWorld {
    var trip: Trip? = Trip(
        id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
        startedAt = 1_000_000L, startTzOffsetMin = 0, createdAt = 1L, updatedAt = 1L,
    )
    val events = mutableListOf(
        event("e1", EventType.BRAKE, 20.0, Confidence.HIGH),
        event("e2", EventType.ACCEL, 40.0, Confidence.LOW),
        event("e3", EventType.CORNER, 50.0, Confidence.MEDIUM),
    )
    val feedback = mutableMapOf<String, EventFeedback>()
    var score: Score? = Score(
        "t1", 80, 80.0, null, null, null, null, null, emptyList(), null, null,
        Tip("accel_progressive", TipCategory.ACCELERATION, "e2", null), null, "v1", 1L,
    )
    var trace: AnalysisTrace? = trace()
    val tracked = mutableListOf<AnalyticsEvent>()

    val trips = object : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))

        override suspend fun trip(id: String): Trip? = trip?.takeIf { it.id == id }

        override suspend fun events(tripId: String): List<DrivingEvent> = events.toList()

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback.toMap()

        override suspend fun score(tripId: String): Score? = score

        override suspend fun delete(tripId: String): FileResult = FileResult.OK
    }

    private val routes = object : RouteRepository {
        override fun observeRoutes(): Flow<List<Route>> = flowOf(emptyList())

        override suspend fun route(id: String): Route? = null

        override suspend fun matchFor(tripId: String): RouteMatch? = null

        override suspend fun personalBest(routeId: String): PersonalBest? = null
    }

    private val store = object : EventFeedbackStore {
        override suspend fun save(feedback: EventFeedback, score: Score?) {
            this@EvidenceWorld.feedback[feedback.eventId] = feedback
            if (score != null) this@EvidenceWorld.score = score
        }
    }

    private val clock = object : Clock {
        override fun monotonicNs(): Long = 0L

        override fun wallMs(): Long = 7L
    }

    val analysis = CountingAnalysis()

    inner class CountingAnalysis : AnalysisFileStore {
        var reads = 0

        override suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef? = null

        override suspend fun read(tripId: String): AnalysisTrace? = trace.also { reads++ }
    }

    /** Re-scoring takes a point off per excluded event, so the tests can see it happened. */
    private val rescorer = com.driveperf.domain.feedback.TripRescorer { r ->
        val excluded = r.feedback.values.count { it.needed || it.inaccurate != null }
        r.score.copy(smoothness = 80 - excluded)
    }

    val markNeeded = MarkEventNeeded(trips, routes, store, rescorer, clock)
    val reportInaccurate = ReportEventInaccurate(trips, routes, store, rescorer, clock)

    val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) {
            tracked += event
        }

        override fun setUsageConsent(allowed: Boolean): Unit = Unit

        override fun resetId(): Unit = Unit
    }

    fun names(): List<String> = tracked.map { it.name }

    companion object {
        fun event(id: String, type: EventType, peak: Double, confidence: Confidence) = DrivingEvent(
            id, "t1", type, peak, peak - 0.8, peak + 0.6, 1.4, 0.42, null, null, null, 13.3, 0.0, confidence, "v1",
        )

        /** 60 s at 5 Hz, one fix a second. */
        fun trace(): AnalysisTrace {
            val n = 300
            return AnalysisTrace(
                5, DoubleArray(n) { -0.001 * it }, DoubleArray(n) { 0.0005 * it }, DoubleArray(n), DoubleArray(n), IntArray(n),
                (0 until 60).map { AnalysisFix(it.toDouble(), 10.0, null, null, null) },
            )
        }
    }
}
