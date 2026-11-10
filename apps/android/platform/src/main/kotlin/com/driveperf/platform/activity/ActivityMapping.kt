package com.driveperf.platform.activity

import com.driveperf.platform.ports.ActivityKind
import com.driveperf.platform.ports.TransitionKind
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import java.util.concurrent.TimeUnit

/** Activity types DrivePerf listens for, in registration order. Everything else is ignored. */
internal val WATCHED_ACTIVITIES: List<Int> = listOf(
    DetectedActivity.IN_VEHICLE,
    DetectedActivity.ON_BICYCLE,
    DetectedActivity.WALKING,
    DetectedActivity.RUNNING,
    DetectedActivity.STILL,
)

internal fun activityKindOf(detectedActivity: Int): ActivityKind? = when (detectedActivity) {
    DetectedActivity.IN_VEHICLE -> ActivityKind.VEHICLE
    DetectedActivity.ON_BICYCLE -> ActivityKind.BICYCLE
    DetectedActivity.WALKING -> ActivityKind.WALKING
    DetectedActivity.RUNNING -> ActivityKind.RUNNING
    DetectedActivity.STILL -> ActivityKind.STILL
    else -> null
}

internal fun transitionKindOf(transitionType: Int): TransitionKind? = when (transitionType) {
    ActivityTransition.ACTIVITY_TRANSITION_ENTER -> TransitionKind.ENTER
    ActivityTransition.ACTIVITY_TRANSITION_EXIT -> TransitionKind.EXIT
    else -> null
}

/** Transitions older than this at delivery are dropped: spike 1 found cached wake events up to 14 h old. */
internal val MAX_TRANSITION_AGE_NS: Long = TimeUnit.MINUTES.toNanos(5)
