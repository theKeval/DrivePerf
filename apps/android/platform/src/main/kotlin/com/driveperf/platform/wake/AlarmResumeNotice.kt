package com.driveperf.platform.wake

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.driveperf.domain.capture.AutoDetectResumeNotice
import com.driveperf.platform.R

/**
 * The F-CAP-15 "Auto-detect is back on" notice on Android: an inexact `AlarmManager` alarm at the end of a pause that
 * posts a notification. No exact-alarm permission is needed: `setAndAllowWhileIdle` may fire a few minutes late in Doze,
 * which is fine for a confirmation. The alarm also starts the process when it is dead, so `Application.onCreate` launches
 * the core, which ends the pause and re-arms its triggers at once. Alarms do not survive a reboot; the core schedules
 * the notice again at every launch while a pause lasts, and the boot receiver causes that launch.
 */
class AlarmResumeNotice(private val context: Context) : AutoDetectResumeNotice {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atWallMs: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWallMs, alarmIntent(context))
    }

    override fun cancel() {
        alarms.cancel(alarmIntent(context))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID: String = "auto_detect"
        const val NOTIFICATION_ID: Int = 7002

        private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ResumeNoticeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Posts the notice; without the notification permission Android drops it, and Home still shows the state. */
        internal fun post(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.auto_detect_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.auto_detect_channel_description)
                    setSound(null, null) // it may arrive while driving: never make noise
                    enableVibration(false)
                },
            )
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID, launch ?: Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_drive)
                .setContentTitle(context.getString(R.string.auto_detect_resumed_title))
                .setContentText(context.getString(R.string.auto_detect_resumed_body))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
        }
    }
}

/** Fires at the end of a pause: posts the notice. Starting the process is what makes the core resume. */
class ResumeNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmResumeNotice.post(context)
    }
}
