package com.driveperf.domain.feedback

import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Trip
import com.driveperf.foundation.Clock
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.Outcome

/**
 * "Not accurate?" (F-RPL-04 AC4): the user says an event did not happen as detected (phone moved, pothole or speed
 * bump, not them driving, other). The answer stays on the device and the event leaves the score: like "Was it
 * needed?", its ledger row is dropped and the trip re-scores at once without R1 (pipeline-and-scoring.md §5.4). The
 * write goes through [EventFeedbackStore], so every later trip's comparison is recomputed as well (§5.7). A null
 * [reason] withdraws the report and counts the event again. A "Needed" mark on the same event is kept.
 *
 * Returns the trip's score after the change: null when the trip has no score (passenger, unscorable, short).
 */
public class ReportEventInaccurate(
    private val trips: TripRepository,
    private val routes: RouteRepository,
    private val store: EventFeedbackStore,
    private val rescorer: TripRescorer,
    private val clock: Clock,
) {
    public suspend operator fun invoke(tripId: String, eventId: String, reason: InaccurateReason?): Outcome<Score?> {
        val trip = trips.trip(tripId)
        val events = if (trip == null) emptyList() else trips.events(tripId)
        return when {
            trip == null -> Outcome.Failure(DomainError.Validation("tripId", "not_found"))
            events.none { it.id == eventId } -> Outcome.Failure(DomainError.Validation("eventId", "not_found"))
            else -> Outcome.Success(change(trip, events, eventId, reason))
        }
    }

    /** Writes the answer and re-scores; the same answer twice changes nothing. */
    private suspend fun change(trip: Trip, events: List<DrivingEvent>, eventId: String, reason: InaccurateReason?): Score? {
        val before = trips.feedback(trip.id)
        val score = trips.score(trip.id)
        val previous = before[eventId]
        if (previous?.inaccurate == reason) return score
        val now = clock.wallMs()
        val changed = previous?.copy(inaccurate = reason, at = now) ?: EventFeedback(eventId, trip.id, false, reason, now)
        val after = before + (eventId to changed)
        val rescored = score?.let {
            rescorer.rescore(RescoreRequest(trip, it, events, after, routes.matchFor(trip.id)?.routeId, now))
        }
        store.save(changed, rescored)
        return rescored ?: score
    }
}
