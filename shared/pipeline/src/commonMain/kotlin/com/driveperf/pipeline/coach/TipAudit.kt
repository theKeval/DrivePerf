package com.driveperf.pipeline.coach

import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind

/**
 * The DRIVE-102 audit of a stored tip (F-SCO-05 AC2, F-SCO-06 AC3, pipeline-and-scoring.md §5.8, §6.2): a tip never
 * comes from an unscorable trip, and never points at an excluded event. Tests run it over everything the finaliser,
 * the feedback re-score and the history recompute write.
 */
public object TipAudit {
    /** Why a tip broke the rules. */
    public enum class Violation {
        /** The trip has no Smoothness, is not a drive or is Unscorable, yet has a tip. */
        TIP_ON_UNSCORABLE_TRIP,

        /** The tip's event is not one of the trip's events. */
        UNKNOWN_EVENT,

        /** The tip's event is marked "needed" or "not accurate". */
        EXCLUDED_EVENT,

        /** The tip's event has LOW confidence, which never anchors a tip. */
        LOW_CONFIDENCE_EVENT,

        /** The tip is not in the §6.1 library under its category. */
        NOT_IN_LIBRARY,
    }

    /** Every rule [score]'s tip breaks; empty when it has no tip or keeps them all. */
    public fun violations(
        trip: Trip,
        score: Score?,
        events: List<DrivingEvent>,
        feedback: Map<String, EventFeedback>,
    ): List<Violation> {
        val tip = score?.tip ?: return emptyList()
        val unscorable = trip.kind != TripKind.DRIVER || trip.quality?.badge == QualityBadge.UNSCORABLE ||
            score.smoothness == null
        return listOfNotNull(
            Violation.TIP_ON_UNSCORABLE_TRIP.takeIf { unscorable },
            Violation.NOT_IN_LIBRARY.takeIf { TipSelector.LIBRARY[tip.category]?.contains(tip.tipId) != true },
            tip.eventId?.let { anchorViolation(it, events, feedback) },
        )
    }

    /** What is wrong with the tip's event [eventId], if anything. */
    private fun anchorViolation(eventId: String, events: List<DrivingEvent>, feedback: Map<String, EventFeedback>): Violation? {
        val event = events.firstOrNull { it.id == eventId }
        val answer = feedback[eventId]
        return when {
            event == null -> Violation.UNKNOWN_EVENT
            answer != null && (answer.needed || answer.inaccurate != null) -> Violation.EXCLUDED_EVENT
            event.confidence == Confidence.LOW -> Violation.LOW_CONFIDENCE_EVENT
            else -> null
        }
    }
}
