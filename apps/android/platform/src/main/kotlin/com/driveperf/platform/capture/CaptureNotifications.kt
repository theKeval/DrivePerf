package com.driveperf.platform.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.driveperf.platform.R
import com.driveperf.platform.location.GpsStatus
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds the recording notification (S-LIVE-05 and S-LIVE-06).
 *
 * On Android 16+ it is a Live Update: a `ProgressStyle` notification that requests promotion, with a status-bar chip
 * of at most 7 characters ("24 min"). Below that it is a standard ongoing notification. It is status only: no score,
 * no speed, nothing that invites a glance while driving. Content is a constant template plus minutes and kilometres,
 * never a coordinate, route or car name.
 */
class CaptureNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val name = context.getString(R.string.capture_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
            .apply {
                description = context.getString(R.string.capture_channel_description)
                setShowBadge(false)
                setSound(null, null) // a recording notification must never make noise while driving
                enableVibration(false)
            }
        manager.createNotificationChannel(channel)
    }

    fun build(startedWallMs: Long, distanceM: Double, gps: GpsStatus, nowWallMs: Long): Notification {
        val minutes = TimeUnit.MILLISECONDS.toMinutes((nowWallMs - startedWallMs).coerceAtLeast(0)).toInt()
        val text = when (gps) {
            GpsStatus.SEARCHING -> context.getString(R.string.capture_notification_searching)
            GpsStatus.LOST -> context.getString(R.string.capture_notification_gps_lost)
            GpsStatus.GOOD -> context.getString(
                R.string.capture_notification_progress, minutes, String.format(Locale.US, "%.1f", distanceM / METRES_PER_KM),
            )
        }
        return builder(text, startedWallMs, minutes).build()
    }

    /** The notification shown the instant the service starts, before the first state arrives. */
    fun buildStarting(startedWallMs: Long): Notification =
        builder(context.getString(R.string.capture_notification_searching), startedWallMs, 0).build()

    @Suppress("NewApi") // the API 36 calls are guarded by the SDK_INT check
    private fun builder(text: String, startedWallMs: Long, minutes: Int): Notification.Builder {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle(context.getString(R.string.capture_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.capture_notification_stop), stopIntent()).build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Live Update: indeterminate ProgressStyle (a drive has no end point), promoted, not colourised.
            builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(context.getString(R.string.capture_notification_chip, minutes))
        } else {
            builder.setWhen(startedWallMs).setShowWhen(true).setUsesChronometer(true)
        }
        return builder
    }

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
        private const val METRES_PER_KM = 1000.0
    }
}
