package com.driveperf.platform.capture

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.foundation.Clock
import com.driveperf.platform.location.FixWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service (type `location`) that keeps the shared core's recorder alive while the app is in the background.
 *
 * It only hosts: it calls `startForeground` at once (the OS gives a few seconds), hands the wake to the core's
 * `TripRecorder`, mirrors the recorder's status into the notification, and stops itself when the recorder is idle
 * again. The core decides whether a wake becomes a trip; a rejected candidate ends the service too. Sticky: after a
 * kill the system restarts it, and the core's `onLaunch` resumes or finishes the trip (§1.7).
 */
class CaptureForegroundService : Service() {
    private val recorder: TripRecorder by inject()
    private val notifications: CaptureNotifications by inject()
    private val fixes: FixWatch by inject()
    private val clock: Clock by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var idleTimeout: Job? = null
    private val tracker = TripClock()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> recorder.stop()
            ACTION_WAKE -> intent.let(CaptureStarter::wakeOf)?.let { wake ->
                goForeground()
                recorder.onWake(wake)
            } ?: endIfIdle()
            else -> goForeground()
        }
        watch()
        // Restarted after a kill with a null intent: the core recovers or ends the trip in onLaunch (§1.7).
        return START_STICKY
    }

    private fun goForeground() {
        notifications.ensureChannel()
        startForeground(
            CaptureNotifications.NOTIFICATION_ID,
            notifications.buildStarting(clock.wallMs()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        // A wake the core drops at once (stale, auto detection off) never leaves idle: end after a grace period.
        idleTimeout?.cancel()
        idleTimeout = scope.launch {
            delay(IDLE_GRACE_MS)
            if (recorder.status.value.state == RecorderState.IDLE) endService()
        }
    }

    private fun watch() {
        if (watching != null) return
        var active = false
        watching = recorder.status.onEach { status ->
            if (status.state != RecorderState.IDLE) {
                active = true
                idleTimeout?.cancel()
                notify(status)
            } else if (active) {
                endService()
            }
        }.launchIn(scope)
    }

    private fun notify(status: RecorderStatus) {
        val started = tracker.startedWallMs(status, clock.wallMs()) ?: return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            CaptureNotifications.NOTIFICATION_ID,
            notifications.build(started, status.distanceM, fixes.status(), clock.wallMs()),
        )
    }

    private fun endIfIdle() {
        if (recorder.status.value.state == RecorderState.IDLE) endService()
    }

    private fun endService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** True while the service exists in this process. */
        @Volatile
        var running: Boolean = false
            private set

        const val ACTION_WAKE = "com.driveperf.platform.action.WAKE"
        const val ACTION_RESUME = "com.driveperf.platform.action.RESUME"
        const val ACTION_STOP = "com.driveperf.platform.action.STOP_CAPTURE"
        private const val IDLE_GRACE_MS = 15_000L
    }
}

/**
 * The wall time a trip started, from the recorder's elapsed time, fixed once per trip so the notification's
 * chronometer does not drift with each status. Null while there is no trip to show (a candidate is not shown).
 */
class TripClock {
    private var tripId: String? = null
    private var startedWallMs = 0L

    fun startedWallMs(status: RecorderStatus, nowWallMs: Long): Long? {
        val id = status.tripId
        if (id == null || status.state == RecorderState.IDLE || status.state == RecorderState.CANDIDATE) return null
        if (id != tripId) {
            tripId = id
            startedWallMs = nowWallMs - (status.elapsedS * MS_PER_S).toLong()
        }
        return startedWallMs
    }

    private companion object {
        const val MS_PER_S = 1000.0
    }
}
