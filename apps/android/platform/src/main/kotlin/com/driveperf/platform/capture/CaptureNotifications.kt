package com.driveperf.platform.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.platform.R
import com.driveperf.platform.location.GpsStatus
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds the recording notification (S-LIVE-05 and S-LIVE-06).
 *
 * Eligible Android 16+ trips use an indeterminate `ProgressStyle` and a short status chip. The standard notification
 * uses the same recording status and chronometer. Automatic capture uses a low-importance channel that also supports
 * promotion.
 */
class CaptureNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val promotion by lazy {
        CaptureNotificationPromotion(context.getSharedPreferences("capture_notification", Context.MODE_PRIVATE))
    }

    fun ensureChannel() {
        manager.createNotificationChannels(
            listOf(
                channel(CHANNEL_ID, R.string.capture_channel_name, NotificationManager.IMPORTANCE_DEFAULT),
                channel(AUTO_CHANNEL_ID, R.string.capture_auto_channel_name, NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    private fun channel(id: String, nameRes: Int, importance: Int) =
        NotificationChannel(id, context.getString(nameRes), importance).apply {
            description = context.getString(R.string.capture_channel_description)
            setShowBadge(false)
            setSound(null, null) // a recording notification must never make noise while driving
            enableVibration(false)
        }

    fun build(status: RecorderStatus, startedWallMs: Long, gps: GpsStatus, nowWallMs: Long): Notification {
        val minutes = TimeUnit.MILLISECONDS.toMinutes((nowWallMs - startedWallMs).coerceAtLeast(0))
        val text = recordingText(status, gps, minutes)
        val builder = builder(status.isManual, R.string.capture_notification_title, text)
            .setWhen(startedWallMs).setShowWhen(true).setUsesChronometer(true)
        if (shouldRequestPromotion(status)) {
            builder.setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true))
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(chipText(minutes))
        }
        return builder.build()
    }

    /** The location service needs a notification even before the recorder confirms a trip. */
    fun buildStarting(isManual: Boolean): Notification = builder(
        isManual,
        R.string.capture_notification_starting,
        context.getString(R.string.capture_notification_searching),
    ).setShowWhen(false).build()

    private fun recordingText(status: RecorderStatus, gps: GpsStatus, minutes: Long): String {
        val progress = context.getString(
            R.string.capture_notification_progress, minutes, String.format(Locale.US, "%.1f", status.distanceM / METRES_PER_KM),
        )
        val detail = when {
            status.state == RecorderState.FINISHING -> R.string.capture_notification_finishing
            status.state == RecorderState.PAUSED -> R.string.capture_notification_paused
            gps == GpsStatus.SEARCHING -> R.string.capture_notification_searching
            gps == GpsStatus.LOST -> R.string.capture_notification_gps_lost
            else -> null
        }
        val text = detail?.let {
            context.getString(R.string.capture_notification_detail, progress, context.getString(it))
        } ?: progress
        return if (status.isManual) text else context.getString(R.string.capture_notification_auto, text)
    }

    private fun chipText(minutes: Long): String =
        if (minutes <= MAX_CHIP_MINUTES) {
            context.getString(R.string.capture_notification_chip, minutes)
        } else {
            context.getString(R.string.capture_notification_chip_hours, (minutes / MINUTES_PER_HOUR).coerceAtMost(MAX_CHIP_HOURS))
        }

    private fun shouldRequestPromotion(status: RecorderStatus): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return promotion.shouldRequest(
            status,
            canPromote = { manager.areNotificationsEnabled() && manager.canPostPromotedNotifications() },
            isPromoted = {
                manager.activeNotifications.any {
                    it.id == NOTIFICATION_ID && it.tag == null &&
                        (it.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
                }
            },
        )
    }

    private fun builder(isManual: Boolean, titleRes: Int, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, if (isManual) CHANNEL_ID else AUTO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle(context.getString(titleRes))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.capture_notification_stop), stopIntent())

    private fun openAppIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, launch ?: Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, CaptureForegroundService::class.java).setAction(CaptureForegroundService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "capture"
        const val NOTIFICATION_ID = 7001
        internal const val AUTO_CHANNEL_ID = "capture_auto"
        private const val MAX_CHIP_MINUTES = 999L
        private const val MAX_CHIP_HOURS = 9999L
        private const val MINUTES_PER_HOUR = 60L
        private const val METRES_PER_KM = 1000.0
    }
}

/** After the first post, a trip may only maintain a promotion that Android still reports as active. */
internal class CaptureNotificationPromotion(private val history: SharedPreferences) {
    fun shouldRequest(status: RecorderStatus, canPromote: () -> Boolean, isPromoted: () -> Boolean): Boolean {
        val tripId = status.tripId
        val isRecording = status.state == RecorderState.RECORDING || status.state == RecorderState.PAUSED
        if (tripId == null || !isRecording) return false
        return requestForTrip(tripId, canPromote, isPromoted)
    }

    private fun requestForTrip(tripId: String, canPromote: () -> Boolean, isPromoted: () -> Boolean): Boolean {
        val firstPost = history.getString(PROMOTION_TRIP_KEY, null) != tripId
        if (!firstPost && history.getBoolean(PROMOTION_BLOCKED_KEY, false)) return false

        val requestPromotion = canPromote() && (firstPost || isPromoted())
        if (firstPost || !requestPromotion) {
            history.edit {
                putString(PROMOTION_TRIP_KEY, tripId)
                putBoolean(PROMOTION_BLOCKED_KEY, !requestPromotion)
            }
        }
        return requestPromotion
    }

    private companion object {
        const val PROMOTION_TRIP_KEY = "promotion_trip"
        const val PROMOTION_BLOCKED_KEY = "promotion_blocked"
    }
}
