package com.driveperf.platform.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.driveperf.platform.ports.ActivityEvent
import com.driveperf.platform.ports.ActivitySource
import com.driveperf.platform.ports.CaptureFailure
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * [ActivitySource] on the Activity Recognition transition API. Collecting registers for enter and exit transitions
 * of the watched activities; cancelling unregisters. The OS result is a hint: the core never starts a trip on it
 * alone (PRD F-CAP-02). Fails with [CaptureFailure.PermissionDenied] without `ACTIVITY_RECOGNITION`.
 */
class ActivityTransitionSource(context: Context) : ActivitySource {
    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)

    @SuppressLint("MissingPermission") // Missing permission is caught below and surfaced as a typed failure.
    override fun events(): Flow<ActivityEvent> = callbackFlow {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            Intent(appContext, ActivityTransitionReceiver::class.java).setComponent(
                ComponentName(appContext, ActivityTransitionReceiver::class.java),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // the OS fills in the result extras
        )
        val transitions = WATCHED_ACTIVITIES.flatMap { activity ->
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map { type ->
                ActivityTransition.Builder().setActivityType(activity).setActivityTransition(type).build()
            }
        }
        try {
            client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent)
        } catch (e: SecurityException) {
            close(CaptureFailure.PermissionDenied("ACTIVITY_RECOGNITION", e))
        }
        val forwarder = launch { ActivityEventBus.events.collect { trySend(it) } }
        awaitClose {
            forwarder.cancel()
            client.removeActivityTransitionUpdates(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private companion object {
        const val REQUEST_CODE = 7201
    }
}
