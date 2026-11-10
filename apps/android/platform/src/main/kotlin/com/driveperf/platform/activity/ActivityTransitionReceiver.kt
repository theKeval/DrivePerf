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
        for (event in result.transitionEvents) {
            if (now - event.elapsedRealTimeNanos > MAX_TRANSITION_AGE_NS) continue // stale: not a wake signal
            val kind = activityKindOf(event.activityType) ?: continue
            val transition = transitionKindOf(event.transitionType) ?: continue
            ActivityEventBus.publish(ActivityEvent(event.elapsedRealTimeNanos, kind, transition))
        }
    }
}
