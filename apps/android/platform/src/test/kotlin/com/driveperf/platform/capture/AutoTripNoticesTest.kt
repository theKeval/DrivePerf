package com.driveperf.platform.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.platform.location.GpsStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class AutoTripNoticesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val analytics = RecordingAnalytics()
    private val autoTrip = RecorderStatus(RecorderState.RECORDING, "auto", false, 120.0, 1_500.0, true)

    @Test
    fun `posted is reported once per confirmed automatic trip that the user can see`() {
        val tracker = AutoTripNoticeTracker(analytics)

        tracker.onRecordingNoticeShown(autoTrip.copy(state = RecorderState.CANDIDATE), visible = true)
        tracker.onRecordingNoticeShown(autoTrip.copy(isManual = true, tripId = "manual"), visible = true)
        assertEquals(emptyList<String>(), analytics.names)

        tracker.onRecordingNoticeShown(autoTrip, visible = true)
        tracker.onRecordingNoticeShown(autoTrip.copy(state = RecorderState.PAUSED), visible = true)
        assertEquals(listOf("auto_trip_notification_posted"), analytics.names)
        assertEquals(emptyMap<String, String>(), analytics.events.single().properties)

        tracker.onRecordingNoticeShown(autoTrip.copy(tripId = "hidden"), visible = false)
        assertEquals(1, analytics.names.size) // notifications off: only the Task Manager shows the service
    }

    @Test
    fun `the notice is visible only with notifications on and its channel not blocked`() {
        CaptureNotifications(context).ensureChannel()
        assertTrue(context.isRecordingNoticeVisible(isManual = false))

        // The user blocks only "Automatic drive recording": the app's notifications stay on.
        manager.deleteNotificationChannel(CaptureNotifications.AUTO_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CaptureNotifications.AUTO_CHANNEL_ID, "Automatic drive recording", NotificationManager.IMPORTANCE_NONE),
        )
        assertTrue(manager.areNotificationsEnabled())
        assertFalse(context.isRecordingNoticeVisible(isManual = false))
        assertTrue(context.isRecordingNoticeVisible(isManual = true))

        // So the tracker is told the notice is hidden and reports nothing, yet still hands over the ended trip.
        val tracker = AutoTripNoticeTracker(analytics)
        tracker.onRecordingNoticeShown(autoTrip, context.isRecordingNoticeVisible(autoTrip.isManual))
        assertEquals(emptyList<String>(), analytics.names)
        assertEquals("auto", tracker.takeEndedTrip())

        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(context.isRecordingNoticeVisible(isManual = true))
    }

    @Test
    fun `a trip followed at once by the next one is handed over when the next one appears`() {
        val tracker = AutoTripNoticeTracker(analytics)
        tracker.onRecordingNoticeShown(autoTrip, visible = true)
        assertNull(tracker.takeEndedTripBefore("auto"))
        assertEquals("auto", tracker.takeEndedTripBefore("next"))
        assertNull(tracker.takeEndedTripBefore("next"))
        assertNull(tracker.takeEndedTrip())
    }

    @Test
    fun `the ended trip is handed over once, and never for a manual trip or a candidate`() {
        val tracker = AutoTripNoticeTracker(analytics)
        assertNull(tracker.takeEndedTrip())

        tracker.onRecordingNoticeShown(autoTrip, visible = false)
        assertEquals("auto", tracker.takeEndedTrip())
        assertNull(tracker.takeEndedTrip())

        tracker.onRecordingNoticeShown(autoTrip.copy(isManual = true, tripId = "manual"), visible = true)
        tracker.onRecordingNoticeShown(autoTrip.copy(state = RecorderState.CANDIDATE, tripId = "candidate"), visible = true)
        assertNull(tracker.takeEndedTrip())
    }

    @Test
    fun `only the automatic notice is marked, and its tap opens the app's launch activity`() {
        val launcher = ComponentName(context, "com.driveperf.app.MainActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        val notifications = CaptureNotifications(context).apply { ensureChannel() }
        val auto = notifications.build(autoTrip, startedWallMs = 0L, gps = GpsStatus.GOOD, nowWallMs = 120_000L)
        val manual = notifications.build(autoTrip.copy(isManual = true), startedWallMs = 0L, gps = GpsStatus.GOOD, nowWallMs = 0L)

        val autoIntent = shadowOf(auto.contentIntent).savedIntent
        val manualIntent = shadowOf(manual.contentIntent).savedIntent
        assertEquals(AutoTripNoticeTaps.SOURCE_AUTO_TRIP, autoIntent.getStringExtra(AutoTripNoticeTaps.EXTRA_SOURCE))
        assertNull(manualIntent.getStringExtra(AutoTripNoticeTaps.EXTRA_SOURCE))
        // Both open the launcher activity, whose in-drive gate shows S-DRV-01 while the trip records (AC2).
        assertEquals(launcher, autoIntent.component)
        assertEquals(launcher, manualIntent.component)
        assertTrue(auto.contentIntent.isActivity)
    }

    @Test
    fun `a tap is counted once, when the activity starts with the marked intent`() {
        val taps = AutoTripNoticeTaps(analytics)
        val marked = Intent().putExtra(AutoTripNoticeTaps.EXTRA_SOURCE, AutoTripNoticeTaps.SOURCE_AUTO_TRIP)
        val activity = Robolectric.buildActivity(Activity::class.java, marked).get()

        taps.onActivityCreated(activity, null)
        taps.onActivityCreated(activity, null) // the extra is consumed, so a second look counts nothing
        taps.onActivityCreated(Robolectric.buildActivity(Activity::class.java, Intent()).get(), null)
        assertEquals(listOf("auto_trip_notification_tapped"), analytics.names)
    }

    @Test
    fun `a recreated activity does not count the tap again`() {
        val taps = AutoTripNoticeTaps(analytics)
        val marked = Intent().putExtra(AutoTripNoticeTaps.EXTRA_SOURCE, AutoTripNoticeTaps.SOURCE_AUTO_TRIP)
        taps.onActivityCreated(Robolectric.buildActivity(Activity::class.java, marked).get(), android.os.Bundle())
        assertEquals(emptyList<String>(), analytics.names)
    }

    @Test
    fun `drive decoded replaces the notice for a finalised driver trip, quietly, with no place`() = runTest {
        val trips = FakeTrips(trip("t1"), score("t1", 82))
        val notice = DriveDecodedNotice(context, trips)

        assertTrue(notice.post("t1"))
        val posted = shadowOf(manager).getNotification("t1", DriveDecodedNotice.NOTIFICATION_ID)
        assertEquals("Drive decoded.", posted.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Smoothness 82", posted.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(DriveDecodedNotice.CHANNEL_ID, posted.channelId)
        val channel = manager.getNotificationChannel(DriveDecodedNotice.CHANNEL_ID)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `drive decoded without a score yet asks the user to open the app`() = runTest {
        assertTrue(DriveDecodedNotice(context, FakeTrips(trip("t2"), score = null)).post("t2"))
        val posted = shadowOf(manager).getNotification("t2", DriveDecodedNotice.NOTIFICATION_ID)
        assertEquals("Open DrivePerf to see your drive.", posted.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun `no drive decoded for a passenger trip, a missing trip or with notifications off`() = runTest {
        assertFalse(DriveDecodedNotice(context, FakeTrips(trip("p", kind = TripKind.PASSENGER), null)).post("p"))
        assertFalse(DriveDecodedNotice(context, FakeTrips(trip = null, score = null)).post("gone"))
        assertFalse(DriveDecodedNotice(context, FakeTrips(trip("d", status = TripStatus.DISCARDED), null)).post("d"))
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(DriveDecodedNotice(context, FakeTrips(trip("t3"), score("t3", 90))).post("t3"))
        assertEquals(0, shadowOf(manager).size())
    }

    private fun trip(id: String, kind: TripKind = TripKind.DRIVER, status: TripStatus = TripStatus.FINALISED) = Trip(
        id = id, status = status, kind = kind, source = TripSource.AUTO, startedAt = 0L, startTzOffsetMin = 0,
        createdAt = 0L, updatedAt = 0L,
    )

    private fun score(tripId: String, smoothness: Int) = Score(
        tripId = tripId, smoothness = smoothness, smoothnessRaw = smoothness.toDouble(), baseline = null, delta = null,
        basis = null, basisRouteId = null, calibrating = null, contributors = emptyList(), ledger = null, efficiency = null,
        tip = null, recordState = null, algorithmVersion = "test", computedAt = 0L,
    )

    private class RecordingAnalytics : Analytics {
        val events = mutableListOf<AnalyticsEvent>()
        val names: List<String> get() = events.map { it.name }

        override fun track(event: AnalyticsEvent) {
            events += event
        }

        override fun setUsageConsent(allowed: Boolean) = Unit

        override fun resetId() = Unit
    }

    private class FakeTrips(private val trip: Trip?, private val score: Score?) : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))

        override suspend fun trip(id: String): Trip? = trip?.takeIf { it.id == id }

        override suspend fun events(tripId: String): List<DrivingEvent> = emptyList()

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = emptyMap()

        override suspend fun score(tripId: String): Score? = score?.takeIf { it.tripId == tripId }

        override suspend fun delete(tripId: String): FileResult = error("not used")
    }
}
