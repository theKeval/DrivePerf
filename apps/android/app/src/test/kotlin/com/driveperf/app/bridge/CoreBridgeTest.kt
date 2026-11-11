package com.driveperf.app.bridge

import com.driveperf.app.core.WakePath
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.feature.capture.CaptureSnapshot
import com.driveperf.feature.capture.FinishedTrip
import com.driveperf.feature.capture.GpsHealth
import com.driveperf.feature.onboarding.PermissionState
import com.driveperf.platform.location.GpsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBridgeTest {
    private val recording = RecorderStatus(RecorderState.RECORDING, "t1", true, 60.0, 1234.0, true)

    @Test
    fun `recorder status maps to what the capture screens show`() {
        assertEquals(
            CaptureSnapshot.Recording(5_000L, 1234.0, GpsHealth.LOST, isManual = true, tripId = "t1"),
            PlatformCaptureController.snapshotOf(recording, 5_000L, GpsStatus.LOST),
        )
        assertEquals(CaptureSnapshot.Idle, PlatformCaptureController.snapshotOf(RecorderStatus.IDLE, null, GpsStatus.SEARCHING))
        assertEquals(
            CaptureSnapshot.Failed,
            PlatformCaptureController.snapshotOf(RecorderStatus.IDLE.copy(startFailed = true), null, GpsStatus.SEARCHING),
        )
    }

    @Test
    fun `an automatic trip is shown as one`() {
        val auto = recording.copy(isManual = false)
        assertEquals(
            CaptureSnapshot.Recording(5_000L, 1234.0, GpsHealth.GOOD, isManual = false, tripId = "t1"),
            PlatformCaptureController.snapshotOf(auto, 5_000L, GpsStatus.GOOD),
        )
    }

    @Test
    fun `the phone still looks like it is moving only while the latest reading is automotive`() {
        fun sample(at: Long, type: ActivityType, confidence: ActivityConfidence = ActivityConfidence.HIGH) =
            ActivitySample(at, at, type, confidence)
        assertFalse(PlatformCaptureController.stillAutomotive(emptyList()))
        assertTrue(PlatformCaptureController.stillAutomotive(listOf(sample(1, ActivityType.AUTOMOTIVE))))
        assertFalse(
            PlatformCaptureController.stillAutomotive(
                listOf(sample(1, ActivityType.AUTOMOTIVE), sample(2, ActivityType.STATIONARY)),
            ),
        )
        assertFalse(
            PlatformCaptureController.stillAutomotive(
                listOf(sample(1, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW)),
            ),
        )
        assertTrue(
            PlatformCaptureController.stillAutomotive(
                listOf(sample(2, ActivityType.AUTOMOTIVE), sample(1, ActivityType.WALKING)),
            ),
        )
    }

    @Test
    fun `only committed trips are finished trips`() {
        val trip = Trip(
            id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.MANUAL, startedAt = 1_000,
            startTzOffsetMin = 0, endedAt = 601_000, distanceM = 5_200.0, durationS = 600.0, createdAt = 1, updatedAt = 2,
        )
        assertEquals(
            FinishedTrip("t1", manual = true, startedAtMs = 1_000, endedAtMs = 601_000, distanceM = 5_200.0, durationS = 600),
            PlatformCaptureController.finishedOf(trip),
        )
        assertTrue(PlatformCaptureController.finishedOf(trip.copy(status = TripStatus.INTERRUPTED))!!.interrupted)
        assertNull(PlatformCaptureController.finishedOf(trip.copy(status = TripStatus.RECORDING)))
        assertNull(PlatformCaptureController.finishedOf(trip.copy(status = TripStatus.DISCARDED)))
    }

    @Test
    fun `automatic trips need the opt-in and fine and background location and activity recognition`() {
        val all = PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true)
        assertTrue(WakePath.automaticAllowed(all, optedIn = true))
        assertFalse(WakePath.automaticAllowed(all.copy(backgroundLocation = false), optedIn = true))
        assertFalse(WakePath.automaticAllowed(all.copy(activityRecognition = false), optedIn = true))
        assertFalse(WakePath.automaticAllowed(all.copy(fineLocation = false), optedIn = true))
    }

    @Test
    fun `automatic trips stay off until the user opts in, whatever is granted`() {
        val all = PermissionState(fineLocation = true, backgroundLocation = true, activityRecognition = true)
        assertFalse(WakePath.automaticAllowed(all, optedIn = false))
    }
}
