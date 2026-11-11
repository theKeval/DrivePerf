package com.driveperf.platform.wake

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.foundation.Clock
import com.driveperf.platform.AndroidClock
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * [ParkedRegionMonitor] as one Play services geofence around the parked car (Spike 1 change 1): leaving it wakes the
 * app with `PARKED_EXIT`, also when the process is dead. Geofences are dropped at reboot; the core re-arms the region
 * from the last parking spot in `onLaunch`, which runs at every process start (`BootReceiver` causes one after boot).
 * Needs fine and background location; without them arming does nothing and the activity wake still works.
 */
class GeofenceParkedRegionMonitor(context: Context) : ParkedRegionMonitor {
    private val appContext = context.applicationContext
    private val client = LocationServices.getGeofencingClient(appContext)

    @SuppressLint("MissingPermission") // a missing permission throws SecurityException, caught below
    override fun arm(center: GeoPoint, radiusM: Double) {
        val fence = Geofence.Builder()
            .setRequestId(REQUEST_ID)
            .setCircularRegion(center.lat, center.lng, radiusM.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(fence).build()
        try {
            client.removeGeofences(listOf(REQUEST_ID))
            client.addGeofences(request, pendingIntent(appContext))
        } catch (_: SecurityException) {
            // No background location: automatic trips rely on the activity wake alone.
        }
    }

    override fun disarm() {
        client.removeGeofences(listOf(REQUEST_ID))
    }

    internal companion object {
        const val REQUEST_ID = "parked"
        const val SOURCE_DETAIL = "geofence"
        private const val REQUEST_CODE = 7203
        private const val NS_PER_MS = 1_000_000L

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // Play services fills in the event
        )

        /** The wake for an exit, timed by the triggering fix when there is one, else now. */
        fun wakeOf(exit: Boolean, fixMonoNs: Long?, fixWallMs: Long?, clock: Clock): WakeEvent? {
            if (!exit) return null
            val t = fixMonoNs ?: clock.monotonicNs()
            val wall = fixWallMs ?: (clock.wallMs() - (clock.monotonicNs() - t) / NS_PER_MS)
            return WakeEvent(WakeKind.PARKED_EXIT, t, wall, SOURCE_DETAIL)
        }
    }
}

/** Receives the parked-region exit, also with the app process dead. Not exported. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) return
        val fix = event.triggeringLocation
        GeofenceParkedRegionMonitor.wakeOf(
            exit = event.geofenceTransition == com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT,
            fixMonoNs = fix?.elapsedRealtimeNanos,
            fixWallMs = fix?.time,
            clock = AndroidClock,
        )?.let(WakeDispatcher::dispatch)
    }
}

/**
 * After a reboot (or an app update), starts the process so `Application.onCreate` re-registers the activity
 * transitions and the core re-arms the parked region. The work is in `onCreate`; this receiver only causes it.
 */
class BootReceiver : BroadcastReceiver() {
    /** True for the broadcasts this receiver is declared for; the process start they cause is the whole job. */
    var lastHandled: String? = null
        private set

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) lastHandled = action
    }
}
