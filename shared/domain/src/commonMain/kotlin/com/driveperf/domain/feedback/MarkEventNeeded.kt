package com.driveperf.domain.feedback

import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
import com.driveperf.foundation.Clock
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.Outcome

/**
 * What a re-score after feedback needs (pipeline-and-scoring.md §5.4). [feedback] is the trip's feedback after the
 * change, by event ID; [routeId] is the trip's route match, if any.
 */
public data class RescoreRequest(
    val trip: Trip,
    val score: Score,
    val events: List<DrivingEvent>,
    val feedback: Map<String, EventFeedback>,
    val routeId: String?,
    val nowWallMs: Long,
)

/** Re-scores a trip from its stored ledger, without R1 (§5.4). Implemented in `:shared:pipeline`. */
public fun interface TripRescorer {
    public suspend fun rescore(request: RescoreRequest): Score
}

/** Writes one event's feedback and the trip's new score in one transaction. Implemented in `:shared:data:local`. */
public interface EventFeedbackStore {
    /** Upserts [feedback]; replaces the trip's score with [score], or leaves it alone when [score] is null. */
    public suspend fun save(feedback: EventFeedback, score: Score?)
}

/**
 * "Was it needed?" (F-SCO-05, decision D4): marking a braking, acceleration or cornering event as needed drops its
 * ledger row, so the trip re-scores at once without R1 (§5.4); "Count it again" (`needed = false`) restores it. The
 * tip is re-chosen and never points at an excluded event. An "inaccurate" flag set elsewhere (F-RPL-04.4) is kept.
 *
 * Returns the trip's score after the change: null when the trip has no score (passenger, unscorable, short).
 */
public class MarkEventNeeded(
    private val trips: TripRepository,
    private val routes: RouteRepository,
    private val store: EventFeedbackStore,
    private val rescorer: TripRescorer,
    private val clock: Clock,
) {
    public suspend operator fun invoke(tripId: String, eventId: String, needed: Boolean): Outcome<Score?> {
        val trip = trips.trip(tripId)
        val events = if (trip == null) emptyList() else trips.events(tripId)
        return when {
            trip == null -> Outcome.Failure(DomainError.Validation("tripId", "not_found"))
            events.none { it.id == eventId } -> Outcome.Failure(DomainError.Validation("eventId", "not_found"))
            else -> Outcome.Success(change(trip, events, eventId, needed))
        }
    }

    /** Writes the answer and re-scores; the same answer twice changes nothing. */
    private suspend fun change(trip: Trip, events: List<DrivingEvent>, eventId: String, needed: Boolean): Score? {
        val before = trips.feedback(trip.id)
        val score = trips.score(trip.id)
        val previous = before[eventId]
        if ((previous?.needed ?: false) == needed) return score
        val now = clock.wallMs()
        val changed = previous?.copy(needed = needed, at = now) ?: EventFeedback(eventId, trip.id, needed, null, now)
        val after = before + (eventId to changed)
        val rescored = score?.let {
            rescorer.rescore(RescoreRequest(trip, it, events, after, routes.matchFor(trip.id)?.routeId, now))
        }
        store.save(changed, rescored)
        return rescored ?: score
    }
}
