package com.driveperf.platform.capture

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.driveperf.platform.AndroidClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

/**
 * Foreground service (type `location`) that keeps capture alive while the app is in the background.
 *
 * It only hosts the machinery: it starts the foreground notification at once (the OS gives a few seconds to do so), then
 * starts the recorder, mirrors the recorder's state into the notification, and stops itself when the trip ends. The
 * recorder starts as early as possible, so nothing from the first seconds is lost (spike 1 change 2). Started from the
 * foreground UI for manual trips; automatic starts from the background arrive with the core's recorder.
 */
class CaptureForegroundService : Service() {
    private val recorder: InterimTripRecorder by inject()
    private val notifications: CaptureNotifications by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTrip()
            else -> startTrip()
        }
        return START_NOT_STICKY
    }

    private fun startTrip() {
        notifications.ensureChannel()
        startForeground(
            CaptureNotifications.NOTIFICATION_ID,
            notifications.buildStarting(AndroidClock.wallMs()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        recorder.start()
        recorder.state.onEach(::onState).launchIn(scope)
    }

    private fun onState(state: CaptureState) {
        when (state) {
            is CaptureState.Recording -> getSystemService(android.app.NotificationManager::class.java)
                .notify(CaptureNotifications.NOTIFICATION_ID, notifications.build(state, AndroidClock.wallMs()))
            is CaptureState.Idle, is CaptureState.Failed -> endService()
        }
    }

    private fun stopTrip() {
        recorder.stop()
        endService()
    }

    private fun endService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.driveperf.platform.action.STOP_CAPTURE"
    }
}
