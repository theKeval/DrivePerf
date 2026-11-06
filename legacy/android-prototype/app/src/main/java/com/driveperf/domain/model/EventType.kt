package com.driveperf.domain.model

/** Types of driving events detected during a trip. */
enum class EventType {
    HARD_BRAKING,
    HARSH_ACCELERATION,
    AGGRESSIVE_CORNERING,
    SPEEDING,
    PHONE_DISTRACTION,
    NIGHT_DRIVING,
    LONG_TRIP,
    IDLING,
    STOP_START,
}
