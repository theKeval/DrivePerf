package com.driveperf.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.driveperf.data.repository.TripRepository
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives Activity Recognition Transition events from Google Play Services.
 *
 * When the user enters a vehicle, this receiver starts a new trip detection
 * sequence. When the user exits a vehicle, it stops the active tracking service.
 *
 * Coroutine work is launched using [goAsync] so the system does not kill the
 * receiver before the background work completes.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var tripRepository: TripRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            when {
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    onEnteredVehicle(context)
                }
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    onExitedVehicle(context)
                }
            }
        }
    }

    private fun onEnteredVehicle(context: Context) {
        val pendingResult = goAsync()
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val tripId = tripRepository.startTrip(System.currentTimeMillis())
                val serviceIntent = Intent(context, TripTrackingService::class.java).apply {
                    action = TripTrackingService.ACTION_START_TRIP
                    putExtra(TripTrackingService.EXTRA_TRIP_ID, tripId)
                }
                context.startForegroundService(serviceIntent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun onExitedVehicle(context: Context) {
        val serviceIntent = Intent(context, TripTrackingService::class.java).apply {
            action = TripTrackingService.ACTION_STOP_TRIP
        }
        context.startService(serviceIntent)
    }
}
