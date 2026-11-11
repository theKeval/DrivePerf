package com.driveperf.platform.capture

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.platform.location.GpsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class CaptureNotificationsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifications = CaptureNotifications(context).apply { ensureChannel() }
    private val history = context.getSharedPreferences("test_promotion", Context.MODE_PRIVATE)
    private val promotion = CaptureNotificationPromotion(history)
    private val autoTrip = RecorderStatus(RecorderState.RECORDING, "auto", false, 120.0, 1_500.0, true)

    @Test
    fun `the automatic channel is quiet and the recording copy identifies auto start`() {
        val manual = build(autoTrip.copy(isManual = true, tripId = "manual"))
        val auto = build(autoTrip)
        val autoChannel = manager.getNotificationChannel(auto.channelId)

        assertEquals(CaptureNotifications.CHANNEL_ID, manual.channelId)
        assertEquals(CaptureNotifications.AUTO_CHANNEL_ID, auto.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, autoChannel.importance)
        assertNull(autoChannel.sound)
        assertFalse(autoChannel.shouldVibrate())
        assertEquals("Recording your drive", auto.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("2 min · 1.5 km", manual.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("Started automatically · 2 min · 1.5 km", auto.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("Stop", auto.actions.single().title)
    }

    @Test
    fun `the candidate is quiet and confirmation gets the first promotion request`() {
        val starting = notifications.buildStarting(isManual = false)
        assertEquals(CaptureNotifications.AUTO_CHANNEL_ID, starting.channelId)
        assertEquals("Preparing to record", starting.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertFalse(NotificationCompat.isRequestPromotedOngoing(starting))
        assertFalse(request(autoTrip.copy(state = RecorderState.CANDIDATE)))
        assertTrue(request(autoTrip))
    }

    @Test
    fun `promotion continues while active and ends for the trip if demoted`() {
        assertTrue(request(autoTrip))
        val paused = autoTrip.copy(state = RecorderState.PAUSED)
        assertTrue(request(paused, promoted = true))
        assertFalse(request(autoTrip.copy(state = RecorderState.FINISHING), promoted = true))
        assertFalse(request(paused, promoted = false))
        assertFalse(request(autoTrip, promoted = true))
        assertTrue(request(autoTrip.copy(tripId = "next")))
    }

    @Test
    fun `a missing Live Update cannot be promoted again after recreation`() {
        assertTrue(request(autoTrip))
        val recreated = CaptureNotificationPromotion(history)
        assertFalse(recreated.shouldRequest(autoTrip, canPromote = { true }, isPromoted = { false }))
    }

    @Test
    fun `promotion unavailable at confirmation keeps the trip on the standard notification`() {
        assertFalse(request(autoTrip, allowed = false))
        assertFalse(request(autoTrip, allowed = true))
        assertTrue(request(autoTrip.copy(tripId = "next"), allowed = true))
    }

    @Test
    fun `Android 15 uses an ongoing notification with the trip chronometer`() {
        val fallback = build(autoTrip.copy(state = RecorderState.PAUSED))
        assertEquals("Started automatically · 2 min · 1.5 km · Paused", fallback.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(START_MS, fallback.`when`)
        assertTrue(fallback.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue((fallback.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertFalse(NotificationCompat.isRequestPromotedOngoing(fallback))
    }

    private fun build(status: RecorderStatus) = notifications.build(status, START_MS, GpsStatus.GOOD, NOW_MS)

    private fun request(status: RecorderStatus, allowed: Boolean = true, promoted: Boolean = false): Boolean =
        promotion.shouldRequest(status, canPromote = { allowed }, isPromoted = { promoted })

    private companion object {
        const val START_MS = 1_000L
        const val NOW_MS = 121_000L
    }
}
