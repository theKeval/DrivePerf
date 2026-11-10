package com.driveperf.data.local

import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
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
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Interruption
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.PartOfDay
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Stop
import com.driveperf.domain.trip.Stretch
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.TurnDirection
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.testing.InMemoryTripFileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalRepositoriesTest {
    private val db = openDatabase(JvmDatabaseDriverFactory().create())
    private val files = InMemoryTripFileStore()

    private val recording = Trip(
        id = "t1", status = TripStatus.RECORDING, kind = TripKind.DRIVER, source = TripSource.AUTO,
        startedAt = 1_000L, startTzOffsetMin = -300, tzId = "Etc/GMT+5",
        captureMeta = CaptureMeta(WakeKind.PARKED_EXIT, null, null, null, null), createdAt = 1_000L, updatedAt = 1_000L,
    )

    private val finished = recording.copy(
        status = TripStatus.FINALISED, endedAt = 600_000L, endTzOffsetMin = -300, partOfDay = PartOfDay.MORNING,
        context = DriveContext.CITY, distanceM = 5_000.0, estimatedDistanceM = 150.0, durationS = 599.0, movingS = 500.0,
        stoppedS = 99.0, stopCount = 2, avgMovingSpeedMps = 10.0, maxSpeedMps = 16.0, maxSpeedAtS = 120.0,
        elevGainM = 5.0, elevLossM = 4.0, elevMinM = 1.0, elevMaxM = 9.0, bbox = BoundingBox(2.0, -27.6, 2.1, -27.5),
        stops = listOf(Stop(100.0, 20.0, 2.05, -27.55), Stop(300.0, 79.0, null, null)),
        cleanest = Stretch(1_200.0, 30.0, 150.0),
        quality = TripQuality(
            QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 99.0, 40.0, 150.0, 560.0, 39.0, 599.0,
            listOf(
                TripSegment(SegmentKind.ESTIMATED, 0.0, 20.0, SegmentReason.ESTIMATED_START),
                TripSegment(SegmentKind.GAP, 200.0, 290.0, SegmentReason.TUNNEL, "gap.tunnel"),
            ),
        ),
        interrupted = Interruption(599.0, InterruptionReason.LOW_BATTERY), suspect = Suspect.TRANSIT,
        pipelineVersion = "pipe-1.0.0", updatedAt = 601_000L,
    )

    private val event = DrivingEvent(
        "e1", "t1", EventType.CORNER, 38.76, 38.0, 39.4, 1.4, 0.452, TurnDirection.LEFT, 2.05, -27.55, 13.9, 9.2,
        Confidence.HIGH, "smooth-1.0.0",
    )

    private val score = Score(
        "t1", 84, 84.2, 81, 3, ScoreBasis.CONTEXT, null, Calibrating(2, 3),
        listOf(Contributor(ContributorId.BRAKING, 91.5, 1.2, Band.SMOOTHER, "braking.smoother", listOf("e1"))),
        Ledger(LedgerRow(512.4, 11.2, 6.1, 5.9), listOf(EventLedgerRow("e1", EventType.CORNER, LedgerRow(2.0, 0.0, 0.0, 3.1))), 1.2, 1.4),
        Efficiency(EfficiencyRating.USUAL, 0.4, 0.2, 0.6, 0.05, 3), Tip("corner_smooth_entry", TipCategory.CORNERING, "e1", "p1"),
        RecordState(false, null, null), "smooth-1.0.0", 601_000L,
    )

    private val route = Route(
        "r1", null, null, RouteState.CANDIDATE, null, null, 2.0, -27.6, 2.1, -27.5, 200.0, "t0", 5_000.0,
        createdAt = 500L, updatedAt = 500L,
    )

    @Test
    fun aRecordingTripIsStoredAndFoundAfterAKill() = runTest {
        val store = LocalTripStore(db, "device-1", coroutineContext)
        store.insertRecording(recording)
        assertEquals(recording, store.recordingTrip())
        store.updateRecording(recording.copy(source = TripSource.MANUAL))
        assertEquals(TripSource.MANUAL, store.recordingTrip()?.source)
        assertTrue(LocalTripRepository(db, files, coroutineContext).observeTrips().first().isEmpty())
        store.deleteNow("t1")
        assertNull(store.recordingTrip())
    }

    @Test
    fun aFinalisedTripRoundTripsWithEverythingDerived() = runTest {
        val store = LocalTripStore(db, "device-1", coroutineContext)
        val trips = LocalTripRepository(db, files, coroutineContext)
        val routes = LocalRouteRepository(db, coroutineContext)
        store.insertRecording(recording)
        val match = RouteMatch("t1", "r1", 0.93, MatchSource.AUTO, false, IneligibleReason.PACE_BAND)
        store.saveFinalised(FinalisedTrip(finished, listOf(event), score, match, route))

        assertEquals(finished, trips.trip("t1"))
        assertEquals(listOf(finished), trips.observeTrips().first())
        assertEquals(listOf(event), trips.events("t1"))
        assertEquals(score, trips.score("t1"))
        assertEquals(match, routes.matchFor("t1"))
        assertEquals(route, routes.route("r1"))
        assertEquals(listOf(route), routes.observeRoutes().first())
        assertTrue(trips.feedback("t1").isEmpty())
        assertNull(routes.personalBest("r1"))
    }

    @Test
    fun refinalisingReplacesDerivedRows() = runTest {
        val store = LocalTripStore(db, "device-1", coroutineContext)
        val trips = LocalTripRepository(db, files, coroutineContext)
        store.saveFinalised(FinalisedTrip(finished, listOf(event), score))
        store.saveFinalised(FinalisedTrip(finished.copy(quality = null)))
        assertTrue(trips.events("t1").isEmpty())
        assertNull(trips.score("t1"))
        assertNull(trips.trip("t1")!!.quality)
    }

    @Test
    fun deletingATripCascadesAndRemovesItsFiles() = runTest {
        val store = LocalTripStore(db, "device-1", coroutineContext)
        val trips = LocalTripRepository(db, files, coroutineContext)
        store.saveFinalised(FinalisedTrip(finished, listOf(event), score))
        files.write("t1", TripFileKind.R0, byteArrayOf(1))
        trips.delete("t1")
        assertNull(trips.trip("t1"))
        assertTrue(trips.events("t1").isEmpty())
        assertNull(trips.score("t1"))
        assertTrue(files.tripIds().isEmpty())
    }

    @Test
    fun historyListsFinishedTripsBeforeATimeNewestFirst() = runTest {
        val store = LocalTripStore(db, "device-1", coroutineContext)
        store.saveFinalised(FinalisedTrip(finished, listOf(event), score, RouteMatch("t1", "r1", 0.9, MatchSource.AUTO, true, null), route))
        store.saveFinalised(FinalisedTrip(finished.copy(id = "t2", startedAt = 2_000L)))
        store.insertRecording(recording.copy(id = "t3", startedAt = 3_000L))
        val history = LocalTripHistory(db, coroutineContext)
        val all = history.finishedBefore(10_000L, 10)
        assertEquals(listOf("t2", "t1"), all.map { it.trip.id })
        assertEquals("r1", all[1].routeId)
        assertEquals(84, all[1].score?.smoothness)
        assertEquals(listOf("t1"), history.finishedBefore(2_000L, 10).map { it.trip.id })
        assertEquals(1, history.finishedBefore(10_000L, 1).size)
    }

    @Test
    fun theLastParkingIsRemembered() = runTest {
        val store = LocalTripStore(db, "device-1", coroutineContext)
        assertNull(store.lastParking())
        val parking = Parking(GeoPoint(2.0, -27.5), 9_000L)
        store.setLastParking(parking)
        assertEquals(parking, store.lastParking())
    }
}
