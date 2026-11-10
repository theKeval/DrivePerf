package com.driveperf.platform.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driveperf.platform.ports.ActivityEvent
import com.driveperf.platform.ports.AndroidClock
import com.google.android.gms.location.ActivityTransitionResult

/** Receives Activity Recognition transitions and publishes the fresh ones on [ActivityEventBus]. Not exported. */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val now = AndroidClock.monotonicNs()
        result.transitionEvents
            .filter { now - it.elapsedRealTimeNanos <= MAX_TRANSITION_AGE_NS } // stale: not a wake signal
            .mapNotNull { event ->
                val kind = activityKindOf(event.activityType)
                val transition = transitionKindOf(event.transitionType)
                if (kind != null && transition != null) ActivityEvent(event.elapsedRealTimeNanos, kind, transition) else null
            }
            .forEach(ActivityEventBus::publish)
    }
}
