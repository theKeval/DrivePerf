package com.driveperf.platform.activity

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.google.android.gms.location.DetectedActivity

/** Maps a Google Play services activity type to the platform-neutral [ActivityType]. */
internal fun activityTypeOf(detectedActivity: Int): ActivityType = when (detectedActivity) {
    DetectedActivity.IN_VEHICLE -> ActivityType.AUTOMOTIVE
    DetectedActivity.ON_BICYCLE -> ActivityType.CYCLING
    DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> ActivityType.WALKING
    DetectedActivity.RUNNING -> ActivityType.RUNNING
    DetectedActivity.STILL -> ActivityType.STATIONARY
    else -> ActivityType.UNKNOWN
}

/**
 * Buckets Play services' 0-100 confidence into the three levels iOS reports, so the core reads one scale on both
 * platforms (pipeline-and-scoring.md section 1.3).
 */
internal fun confidenceOf(percent: Int): ActivityConfidence = when {
    percent >= HIGH_FROM_PERCENT -> ActivityConfidence.HIGH
    percent >= MEDIUM_FROM_PERCENT -> ActivityConfidence.MEDIUM
    else -> ActivityConfidence.LOW
}

/**
 * Turns the stream of "most probable activity" readings into change events: a sample is emitted only when the type or
 * the confidence level differs from the last one emitted, and its time is when the new state was first seen, which is
 * what [ActivitySample.tMonoNs] means ("when the activity started"). Not thread-safe: the OS delivers on one thread.
 */
internal class ActivityChangeFilter {
    private var last: ActivitySample? = null

    fun onReading(sample: ActivitySample): ActivitySample? {
        val previous = last
        val changed = previous == null || previous.type != sample.type || previous.confidence != sample.confidence
        if (changed) last = sample
        return if (changed) sample else null
    }
}

private const val HIGH_FROM_PERCENT = 75
private const val MEDIUM_FROM_PERCENT = 50
