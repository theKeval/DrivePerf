package com.driveperf.domain.feedback

import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.foundation.Clock
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkEventNeededTest {
    private val trip = Trip(
        id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
        startedAt = 1L, startTzOffsetMin = 0, createdAt = 1L, updatedAt = 1L,
    )
    private val event = DrivingEvent(
        "e1", "t1", EventType.BRAKE, 10.0, 9.0, 11.0, 2.0, 0.4, null, null, null, 10.0, 0.0, Confidence.HIGH, "smooth-1.0.0",
    )
    private val score = Score("t1", 60, 60.0, null, null, null, null, null, emptyList(), null, null, null, null, "smooth-1.0.0", 1L)

    private var storedScore: Score? = score
    private val feedback = mutableMapOf<String, EventFeedback>()
    private val saved = mutableListOf<Pair<EventFeedback, Score?>>()
    private val requests = mutableListOf<RescoreRequest>()

    private val trips = object : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOf(trip))

        override suspend fun trip(id: String) = trip.takeIf { it.id == id }

        override suspend fun events(tripId: String) = listOf(event).filter { it.tripId == tripId }

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback.toMap()

        override suspend fun score(tripId: String) = storedScore

        override suspend fun delete(tripId: String) = FileResult.OK
    }
    private val routes = object : RouteRepository {
        override fun observeRoutes(): Flow<List<Route>> = flowOf(emptyList())

        override suspend fun route(id: String): Route? = null

        override suspend fun matchFor(tripId: String) =
            RouteMatch(tripId, "r1", 0.9, MatchSource.AUTO, true, null)

        override suspend fun personalBest(routeId: String): PersonalBest? = null
    }
    private val store = object : EventFeedbackStore {
        override suspend fun save(feedback: EventFeedback, score: Score?) {
            saved += feedback to score
            this@MarkEventNeededTest.feedback[feedback.eventId] = feedback
            if (score != null) storedScore = score
        }
    }
    private val clock = object : Clock {
        override fun monotonicNs() = 0L

        override fun wallMs() = 42L
    }
    private val useCase = MarkEventNeeded(trips, routes, store, { r -> requests += r; r.score.copy(smoothness = 70) }, clock)

    @Test
    fun markingNeededRescoresWithTheNewFeedbackAndTheRoute() = runTest {
        val result = useCase("t1", "e1", needed = true)
        assertEquals(Outcome.Success(score.copy(smoothness = 70)), result)
        val request = requests.single()
        assertEquals(true, request.feedback.getValue("e1").needed)
        assertEquals("r1", request.routeId)
        assertEquals(42L, request.nowWallMs)
        assertEquals(EventFeedback("e1", "t1", true, null, 42L) to score.copy(smoothness = 70), saved.single())
    }

    @Test
    fun countingItAgainKeepsAnInaccurateFlagAndRepeatsAreNoOps() = runTest {
        feedback["e1"] = EventFeedback("e1", "t1", true, InaccurateReason.POTHOLE, 5L)
        useCase("t1", "e1", needed = false)
        assertEquals(EventFeedback("e1", "t1", false, InaccurateReason.POTHOLE, 42L), saved.single().first)
        assertEquals(Outcome.Success(storedScore), useCase("t1", "e1", needed = false))
        assertEquals(1, saved.size, "the same answer twice writes nothing")
    }

    @Test
    fun aTripWithoutAScoreStillKeepsTheFeedback() = runTest {
        storedScore = null
        assertEquals(Outcome.Success(null), useCase("t1", "e1", needed = true))
        assertTrue(requests.isEmpty())
        assertNull(saved.single().second)
    }

    @Test
    fun unknownTripsAndEventsAreValidationErrors() = runTest {
        assertEquals(Outcome.Failure(DomainError.Validation("tripId", "not_found")), useCase("nope", "e1", true))
        assertEquals(Outcome.Failure(DomainError.Validation("eventId", "not_found")), useCase("t1", "nope", true))
        assertTrue(saved.isEmpty())
    }
}
