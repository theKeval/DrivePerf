package com.driveperf.domain.trip

/** Detected event type (§4). */
public enum class EventType { BRAKE, ACCEL, CORNER }

/** Estimated confidence (§4.4). */
public enum class Confidence { HIGH, MEDIUM, LOW }

/** Corner direction. */
public enum class TurnDirection { LEFT, RIGHT }

/** Why the user says an event is wrong (F-RPL-04.4). */
public enum class InaccurateReason { PHONE_MOVED, POTHOLE, NOT_ME, OTHER }

/**
 * A detected event (data-model.md §1.9, pipeline-and-scoring.md §4.5). [lat]/[lng] are null when the nearest gated
 * fix is more than 3 s away: listed, not pinned. The evidence trace is not stored; it comes from R1 on demand.
 */
public data class DrivingEvent(
    val id: String,
    val tripId: String,
    val type: EventType,
    val tPeakS: Double,
    val tStartS: Double,
    val tEndS: Double,
    val durationS: Double,
    val peakG: Double,
    val direction: TurnDirection?,
    val lat: Double?,
    val lng: Double?,
    val speedBeforeMps: Double?,
    val speedAfterMps: Double?,
    val confidence: Confidence,
    val algorithmVersion: String,
)

/** The user's feedback on one event (F-SCO-05, F-RPL-04.4). Either flag removes the event from the score. */
public data class EventFeedback(
    val eventId: String,
    val tripId: String,
    val needed: Boolean,
    val inaccurate: InaccurateReason?,
    val at: Long,
)
