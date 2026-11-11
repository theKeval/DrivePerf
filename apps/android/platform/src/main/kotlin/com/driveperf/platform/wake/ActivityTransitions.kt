package com.driveperf.platform.wake

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.foundation.Clock
import com.driveperf.platform.AndroidClock
import com.driveperf.platform.activity.ActivityLog
import com.driveperf.platform.activity.activityTypeOf
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * The Activity Recognition Transition API wake (pipeline-and-scoring.md §1.4 `ACTIVITY_VEHICLE`): Play services
 * delivers `IN_VEHICLE` enter to [ActivityTransitionReceiver] even when the app process is dead, so automatic trips
 * start after the app was killed. The registration survives process death but not a reboot; [arm] runs at every
 * process start, which `BootReceiver` causes after a reboot.
 *
 * Transitions carry no confidence, so an `ENTER` is read as that activity at `HIGH` (it is Play services' settled
 * decision) and goes into [ActivityLog], where `ActivitySource.queryHistory` finds it for the candidate check (§1.5).
 */
class ActivityTransitions(context: Context) {
    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)

    /** Registers the transitions. False when `ACTIVITY_RECOGNITION` is not granted. Calling it again replaces it. */
    @SuppressLint("MissingPermission") // a missing permission throws SecurityException, caught below
    fun arm(): Boolean = try {
        client.requestActivityTransitionUpdates(ActivityTransitionRequest(TRANSITIONS), pendingIntent(appContext))
        true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun disarm() {
        try {
            client.removeActivityTransitionUpdates(pendingIntent(appContext))
        } catch (_: SecurityException) {
            // The registration died with the permission.
        }
    }

    internal companion object {
        private const val REQUEST_CODE = 7202
        private const val NS_PER_MS = 1_000_000L
        const val SOURCE_DETAIL = "activity_transition"

        private val WATCHED = listOf(
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.WALKING, DetectedActivity.RUNNING,
            DetectedActivity.STILL,
        )
        val TRANSITIONS: List<ActivityTransition> = WATCHED.map {
            ActivityTransition.Builder().setActivityType(it).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build()
        }

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // Play services fills in the result
        )

        /** The activity sample an `ENTER` stands for, timed by its own clock reading; null for an `EXIT`. */
        fun sampleOf(event: ActivityTransitionEvent, clock: Clock): ActivitySample? {
            if (event.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) return null
            val t = event.elapsedRealTimeNanos
            val wall = clock.wallMs() - (clock.monotonicNs() - t) / NS_PER_MS
            return ActivitySample(t, wall, activityTypeOf(event.activityType), ActivityConfidence.HIGH)
        }

        /** The wake for [samples]: the latest vehicle entry, if any. */
        fun wakeOf(samples: List<ActivitySample>): WakeEvent? = samples
            .lastOrNull { it.type == com.driveperf.domain.capture.ActivityType.AUTOMOTIVE }
            ?.let { WakeEvent(WakeKind.ACTIVITY_VEHICLE, it.tMonoNs, it.wallMs, SOURCE_DETAIL) }
    }
}

/** Receives activity transitions, also with the app process dead. Not exported. */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = if (ActivityTransitionResult.hasResult(intent)) ActivityTransitionResult.extractResult(intent) else null
        val samples = result?.transitionEvents.orEmpty().mapNotNull { ActivityTransitions.sampleOf(it, AndroidClock) }
        samples.forEach(ActivityLog::add)
        ActivityTransitions.wakeOf(samples)?.let(WakeDispatcher::dispatch)
    }
}
