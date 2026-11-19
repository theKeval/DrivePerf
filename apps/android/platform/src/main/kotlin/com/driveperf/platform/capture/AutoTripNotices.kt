package com.driveperf.platform.capture

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.OnNewIntentProvider
import androidx.core.util.Consumer
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripStatus
import com.driveperf.platform.R

/** `auto_trip_notification_posted` (F-CAP-11): the recording notice of an automatic trip was shown. No properties. */
data object AutoTripNotificationPosted : AnalyticsEvent {
    override val name: String = "auto_trip_notification_posted"
    override val properties: Map<String, String> = emptyMap()
}

/** `auto_trip_notification_tapped` (F-CAP-11): the user opened the app from that notice. No properties. */
data object AutoTripNotificationTapped : AnalyticsEvent {
    override val name: String = "auto_trip_notification_tapped"
    override val properties: Map<String, String> = emptyMap()
}

/**
 * Whether the user can see the recording notice: notifications are on for the app and the notice's channel is not
 * blocked. Either off leaves only the Task Manager entry (F-CAP-11 edge case), so the notice is not counted as posted.
 */
fun Context.isRecordingNoticeVisible(isManual: Boolean): Boolean {
    val manager = getSystemService(NotificationManager::class.java)
    if (!manager.areNotificationsEnabled()) return false
    val channel = manager.getNotificationChannel(if (isManual) CaptureNotifications.CHANNEL_ID else CaptureNotifications.AUTO_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

/**
 * The automatic trip's recording notice (S-LIVE-09, DRIVE-137) across the foreground service's lifetime: it reports
 * `auto_trip_notification_posted` once per confirmed automatic trip, and remembers that trip so the service can replace
 * the notice with "Drive decoded." when the trip ends (AC3). A candidate is never a trip here; manual trips have their
 * own surfaces. One instance per process, so a restarted service does not report the same trip again.
 */
class AutoTripNoticeTracker(private val analytics: Analytics) {
    private var postedTripId: String? = null
    private var confirmedTripId: String? = null

    /** The service showed the recording notification for [status]; [visible] is false while notifications are off. */
    @Synchronized
    fun onRecordingNoticeShown(status: RecorderStatus, visible: Boolean) {
        val tripId = status.tripId ?: return
        val confirmed = status.state == RecorderState.RECORDING || status.state == RecorderState.PAUSED
        if (status.isManual || !confirmed) return
        confirmedTripId = tripId
        if (visible && postedTripId != tripId) {
            postedTripId = tripId
            analytics.track(AutoTripNotificationPosted)
        }
    }

    /** The confirmed automatic trip that just ended, once; null after a manual trip or a rejected candidate. */
    @Synchronized
    fun takeEndedTrip(): String? = confirmedTripId.also { confirmedTripId = null }
}

/**
 * Counts taps on the automatic trip's recording notice. Its content intent carries [EXTRA_SOURCE]; this sees it when the
 * activity starts or receives a new intent, so the activity itself needs no code for it. The intent opens the app,
 * whose in-drive gate shows S-DRV-01 while the trip records (AC2).
 */
class AutoTripNoticeTaps(private val analytics: Analytics) : Application.ActivityLifecycleCallbacks {
    private val listeners = mutableMapOf<Activity, Consumer<Intent>>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) consume(activity.intent) // a recreated activity re-delivers an old intent
        if (activity is OnNewIntentProvider) {
            val listener = Consumer<Intent> { consume(it) }
            activity.addOnNewIntentListener(listener)
            listeners[activity] = listener
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        val listener = listeners.remove(activity) ?: return
        (activity as? OnNewIntentProvider)?.removeOnNewIntentListener(listener)
    }

    internal fun consume(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_SOURCE) != SOURCE_AUTO_TRIP) return
        intent.removeExtra(EXTRA_SOURCE)
        analytics.track(AutoTripNotificationTapped)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    companion object {
        const val EXTRA_SOURCE = "com.driveperf.platform.extra.NOTIFICATION_SOURCE"
        const val SOURCE_AUTO_TRIP = "auto_trip"
    }
}

/**
 * "Drive decoded." (S-LIVE-09 replaced state, F-STY-02 copy): after an automatic trip is finalised, the service removes
 * the recording notice and posts this one on its own quiet channel. Only for a finalised driver trip, and only when
 * notifications are allowed. No map, place or number other than the Smoothness score (DRIVE-137 privacy). Quiet hours
 * and coalescing several drives belong to F-STY-02.
 */
class DriveDecodedNotice(private val context: Context, private val trips: TripRepository) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.decoded_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = context.getString(R.string.decoded_channel_description)
                    setShowBadge(true)
                    setSound(null, null) // never a sound or vibration on a notification (motion.md §6)
                    enableVibration(false)
                },
        )
    }

    /** Posts the notice for [tripId] and returns true, or returns false when the trip or the user's settings rule it out. */
    suspend fun post(tripId: String): Boolean {
        val text = if (manager.areNotificationsEnabled()) text(tripId) else null
        if (text == null) return false
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle(context.getString(R.string.decoded_notification_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp())
            .build()
        manager.notify(tripId, NOTIFICATION_ID, notification)
        return true
    }

    /** The body for a finalised driver trip ("Smoothness 82", or a prompt until it is scored); null for any other trip. */
    private suspend fun text(tripId: String): String? {
        val trip = trips.trip(tripId)?.takeIf { it.status == TripStatus.FINALISED && it.kind == TripKind.DRIVER }
            ?: return null
        return trips.score(trip.id)?.smoothness?.let { context.getString(R.string.decoded_notification_score, it) }
            ?: context.getString(R.string.decoded_notification_no_score)
    }

    private fun openApp(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, REQUEST_CODE, launch ?: Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "drive_decoded"
        const val NOTIFICATION_ID = 7002
        private const val REQUEST_CODE = 3
    }
}
