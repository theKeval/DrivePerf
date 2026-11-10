package com.driveperf.domain

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionCapability
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.Quat
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.RecorderStatus
import com.driveperf.domain.capture.ThermalState
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.RecordEntry
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Efficiency
import com.driveperf.domain.score.EfficiencyRating
import com.driveperf.domain.score.EventLedgerRow
import com.driveperf.domain.score.Ledger
import com.driveperf.domain.score.LedgerRow
import com.driveperf.domain.score.RecordState
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.CaptureMeta
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Interruption
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.PartOfDay
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Stop
import com.driveperf.domain.trip.Stretch
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.TurnDirection
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntitiesTest {
    @Test
    fun samplesCarryPlatformValuesUnchanged() {
        val fix = LocationSample(1L, 2L, 3.0, -27.0, 10.0, 4.0, 3.0, 13.9, 0.3, 90.0, 5.0)
        val motion = MotionSample(1L, Vec3(0.0, 0.1, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, 2.0), Quat(1.0, 0.0, 0.0, 0.0))
        val activity = ActivitySample(1L, 2L, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH)
        assertEquals(13.9, fix.speedMps)
        assertEquals(1.0, motion.gravityUp.z)
        assertEquals(ActivityType.AUTOMOTIVE, activity.type)
        assertEquals(Vec3(1.0, 1.0, 1.0), RecordedAccelSample(5L, Vec3(1.0, 1.0, 1.0)).accelG)
        assertEquals(false, MotionCapability(hasGyroscope = false, maxRateHz = 50).hasGyroscope)
        assertEquals(ThermalState.SERIOUS, PowerStatus(5, isCharging = false, lowPowerMode = true, ThermalState.SERIOUS).thermal)
        assertEquals(GeoPoint(1.0, 2.0), GeoPoint(1.0, 2.0))
    }

    @Test
    fun wakeEventsDefaultToNoSourceDetail() {
        assertNull(WakeEvent(WakeKind.PARKED_EXIT, 1L, 2L).sourceDetail)
        assertEquals("control_center", WakeEvent(WakeKind.MANUAL, 1L, 2L, "control_center").sourceDetail)
        assertEquals(5, WakeKind.entries.size)
    }

    @Test
    fun idleStatusShowsNothingLive() {
        assertEquals(RecorderState.IDLE, RecorderStatus.IDLE.state)
        assertNull(RecorderStatus.IDLE.tripId)
        assertEquals(0.0, RecorderStatus.IDLE.distanceM)
    }

    @Test
    fun ledgerRowsAddElementWise() {
        val sum = LedgerRow(1.0, 2.0, 3.0, 4.0) + LedgerRow(0.5, 0.5, 0.5, 0.5)
        assertEquals(LedgerRow(1.5, 2.5, 3.5, 4.5), sum)
        assertEquals(sum, sum + LedgerRow.ZERO)
    }

    @Test
    fun aFinalisedTripHoldsEveryDerivedBlock() {
        val trip = Trip(
            id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO,
            sourceDetail = null, vehicleId = "v1", startedAt = 1L, startTzOffsetMin = -300, startEstimated = true,
            endedAt = 2L, endTzOffsetMin = -300, tzId = "Etc/GMT+5", partOfDay = PartOfDay.MORNING,
            fromLabel = "Home", toLabel = "Work", labelOverride = null, context = DriveContext.CITY,
            distanceM = 1000.0, estimatedDistanceM = 100.0, durationS = 300.0, movingS = 250.0, stoppedS = 50.0,
            stopCount = 1, avgMovingSpeedMps = 4.0, maxSpeedMps = 14.0, maxSpeedAtS = 20.0, elevGainM = 5.0,
            elevLossM = 5.0, elevMinM = 1.0, elevMaxM = 6.0, bbox = BoundingBox(0.0, -30.0, 1.0, -29.0),
            stops = listOf(Stop(100.0, 20.0, null, null)), cleanest = Stretch(800.0, 10.0, 90.0),
            quality = TripQuality(
                QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 100.0, 40.0, 150.0, 280.0, 20.0, 300.0,
                listOf(TripSegment(SegmentKind.ESTIMATED, 0.0, 20.0, SegmentReason.ESTIMATED_START)),
            ),
            interrupted = Interruption(299.0, InterruptionReason.APP_KILLED), suspect = Suspect.TRANSIT,
            suspectAnswer = SuspectAnswer.NO, captureMeta = CaptureMeta(WakeKind.PARKED_EXIT, 0.1, 100.0, 80, 79),
            pipelineVersion = "pipe-1.0.0", rawState = RawState.FULL, createdAt = 1L, updatedAt = 2L, deletedAt = null,
        )
        assertEquals(QualityBadge.GOOD, trip.quality?.badge)
        assertNull(trip.quality?.spans?.single()?.labelKey)
        assertEquals(PreviewRow(0.0, 1.0, 2.0, null, null, null, null, 0).quality, 0)
        assertEquals(QualityReason.entries.size, 8)
    }

    @Test
    fun eventsAndFeedback() {
        val event = DrivingEvent(
            "e1", "t1", EventType.CORNER, 25.36, 24.9, 25.9, 1.0, 0.422, TurnDirection.LEFT, null, null, 13.9, 13.9,
            Confidence.MEDIUM, "smooth-1.0.0",
        )
        val feedback = EventFeedback("e1", "t1", needed = true, inaccurate = InaccurateReason.POTHOLE, at = 3L)
        assertEquals(TurnDirection.LEFT, event.direction)
        assertEquals(InaccurateReason.POTHOLE, feedback.inaccurate)
    }

    @Test
    fun scoresAndRoutes() {
        val ledger = Ledger(LedgerRow.ZERO, listOf(EventLedgerRow("e1", EventType.BRAKE, LedgerRow.ZERO)), 1.2, 1.2)
        val score = Score(
            "t1", 88, 87.8, 85, 3, ScoreBasis.CONTEXT, null, Calibrating(2, 3),
            listOf(Contributor(ContributorId.BRAKING, 92.0, 1.0, Band.SMOOTHER, "brake.smoother", listOf("e1"))),
            ledger, Efficiency(EfficiencyRating.BETTER, 0.5, 0.1, 0.4, 0.05, 3),
            Tip("brake_light", TipCategory.BRAKING, "e1", "p1"), RecordState(true, 80, "t0"), "smooth-1.0.0", 1L,
        )
        val route = Route(
            "r1", null, RoutePreset.COMMUTE, RouteState.CANDIDATE, null, null, 1.0, -29.0, 1.1, -29.1, 200.0, "t1",
            5000.0, createdAt = 1L, updatedAt = 1L,
        )
        val match = RouteMatch("t1", "r1", 0.9, MatchSource.AUTO, false, IneligibleReason.PACE_BAND)
        val best = PersonalBest("r1", "t1", 88, 1L, listOf(RecordEntry("t1", 88, 1L)))
        assertEquals(88, score.smoothness)
        assertEquals(true, route.isActive)
        assertEquals(IneligibleReason.PACE_BAND, match.ineligibleReason)
        assertEquals(1, best.history.size)
        assertEquals(TripFileKind.R1, TripFileKind.valueOf("R1"))
        assertEquals(FileResult.DISK_FULL, FileResult.valueOf("DISK_FULL"))
    }
}
