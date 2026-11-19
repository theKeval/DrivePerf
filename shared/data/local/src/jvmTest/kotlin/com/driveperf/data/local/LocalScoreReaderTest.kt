package com.driveperf.data.local

import com.driveperf.data.local.db.Event_feedback
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.MatchSource
import com.driveperf.domain.route.Route
import com.driveperf.domain.route.RouteMatch
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.route.RouteState
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.RecordState
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.TripQuality
import com.driveperf.testing.InMemoryTripFileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalScoreReaderTest {
    private val driver = JvmDatabaseDriverFactory().create()
    private val db = openDatabase(driver)

    private fun trip(id: String, startedAt: Long, kind: TripKind = TripKind.DRIVER, vehicle: String? = null,
        spans: List<TripSegment> = emptyList()) =
        Trip(
            id = id, status = TripStatus.FINALISED, kind = kind, source = TripSource.AUTO, vehicleId = vehicle,
            startedAt = startedAt, startTzOffsetMin = 0, endedAt = startedAt + 600_000L, distanceM = 5_000.0, durationS = 600.0,
            quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 99.0, 40.0, null, 560.0, 0.0, 600.0, spans),
            createdAt = startedAt, updatedAt = startedAt,
        )

    private fun score(id: String, smoothness: Int?, record: Boolean = false) = Score(
        id, smoothness, smoothness?.toDouble(), 80, smoothness?.let { it - 80 }, null, null,
        if (smoothness == null) null else Calibrating(2, 3), emptyList(), null, null, null,
        RecordState(record, null, null), "smooth-1.0.0", 1L,
    )

    private val route = Route(
        "r1", "The Commute", RoutePreset.COMMUTE, RouteState.ROUTE, null, null, 2.0, -27.6, 2.1, -27.5, 200.0, "t1", 5_000.0,
        createdAt = 500L, updatedAt = 500L,
    )

    private fun match(id: String, eligible: Boolean = true, reason: IneligibleReason? = null) =
        RouteMatch(id, "r1", 0.9, MatchSource.AUTO, eligible, reason)

    private fun addCar(id: String, nickname: String?, make: String?, model: String?, active: Boolean = true) {
        driver.execute(
            null,
            "INSERT INTO vehicle (id, nickname, make, model, is_active, added_at, created_at, updated_at, origin_device_id) " +
                "VALUES (?, ?, ?, ?, ?, 1, 1, 1, 'd')",
            5,
        ) {
            bindString(0, id)
            bindString(1, nickname)
            bindString(2, make)
            bindString(3, model)
            bindLong(4, if (active) 1L else 0L)
        }
    }

    @Test
    fun linesCarryTheScoreRouteAndCarOfEachListedTrip() = runTest {
        val store = LocalTripStore(db, "d", coroutineContext)
        addCar("gti", "GTI", "Volkswagen", "Golf GTI")
        addCar("ioniq", null, "Hyundai", "Ioniq 5")
        addCar("old", "Old", null, null, active = false)
        store.saveFinalised(FinalisedTrip(trip("t1", 1_000L, vehicle = "gti"), score = score("t1", 85, record = true),
            routeMatch = match("t1"), newRoute = route))
        store.saveFinalised(FinalisedTrip(trip("t2", 2_000L, vehicle = "ioniq"), score = score("t2", null)))
        store.saveFinalised(FinalisedTrip(trip("t3", 3_000L)))

        val lines = LocalScoreReader(db, coroutineContext).observeLines().first()

        assertEquals(2, lines.carCount)
        val first = lines.lines.getValue("t1")
        assertEquals(85, first.smoothness)
        assertEquals(5, first.delta)
        assertEquals(Calibrating(2, 3), first.calibrating)
        assertTrue(first.newRecord)
        assertEquals(RouteName("r1", "The Commute", RoutePreset.COMMUTE), first.route)
        assertEquals("GTI", first.car)
        assertEquals("Hyundai Ioniq 5", lines.lines.getValue("t2").car)
        assertNull(lines.lines.getValue("t2").smoothness)
        val unscored = lines.lines.getValue("t3")
        assertNull(unscored.smoothness)
        assertNull(unscored.route)
        assertNull(unscored.car)
        assertFalse(unscored.newRecord)
    }

    @Test
    fun theDetailFollowsTheScoreEventsFeedbackAndRouteRuns() = runTest {
        val store = LocalTripStore(db, "d", coroutineContext)
        val event = DrivingEvent("e1", "t3", EventType.BRAKE, 40.0, 39.0, 41.0, 2.0, 0.41, null, null, null, 12.0, 0.0,
            Confidence.HIGH, "smooth-1.0.0")
        store.saveFinalised(FinalisedTrip(trip("t1", 1_000L), score = score("t1", 70), routeMatch = match("t1"), newRoute = route))
        store.saveFinalised(FinalisedTrip(trip("t2", 2_000L, kind = TripKind.PASSENGER), score = score("t2", null),
            routeMatch = match("t2", false, IneligibleReason.PASSENGER), newRoute = route))
        store.saveFinalised(FinalisedTrip(trip("t3", 3_000L), listOf(event), score("t3", 85), match("t3", false,
            IneligibleReason.PACE_BAND), route))
        store.saveFinalised(FinalisedTrip(trip("t4", 4_000L), score = score("t4", 90), routeMatch = match("t4"), newRoute = route))
        db.tripQueries.upsertFeedback(Event_feedback("e1", "t3", 1L, null, 5L))

        val reader = LocalScoreReader(db, coroutineContext)
        val detail = reader.observeDetail("t3").first()

        assertEquals(85, detail.score?.smoothness)
        assertEquals(listOf(event), detail.events)
        assertTrue(detail.feedback.getValue("e1").needed)
        assertEquals("r1", detail.route?.routeId)
        assertEquals(IneligibleReason.PACE_BAND, detail.recordIneligible)
        assertNull(detail.car)
        // Runs up to and including this drive, oldest first: the passenger run and the later run are left out.
        assertEquals(listOf(70, 85), detail.runs)

        val missing = reader.observeDetail("nope").first()
        assertNull(missing.score)
        assertNull(missing.route)
        assertTrue(missing.runs.isEmpty())
    }

    @Test
    fun theDetailFollowsATripMovedToAnotherCar() = runTest {
        addCar("gti", "GTI", "Volkswagen", "Golf GTI")
        addCar("ioniq", "Ioniq", "Hyundai", "Ioniq 5")
        LocalTripStore(db, "d", coroutineContext).saveFinalised(FinalisedTrip(trip("t1", 1_000L, vehicle = "gti"), score = score("t1", 80)))
        val reader = LocalScoreReader(db, coroutineContext)
        val cars = mutableListOf<String?>()
        val watching = backgroundScope.launch { reader.observeDetail("t1").collect { cars += it.car } }
        testScheduler.runCurrent()
        assertEquals(listOf<String?>("GTI"), cars)

        LocalTripVehicles(db, coroutineContext, com.driveperf.testing.TestClock()).setVehicle("t1", "ioniq")
        testScheduler.runCurrent()

        assertEquals("Ioniq", cars.last())
        assertEquals("Ioniq", reader.observeLines().first().lines.getValue("t1").car)
        watching.cancel()
    }

    @Test
    fun aThousandTripsReadWithTheirSpansAndScoresWellUnderASecond() = runTest {
        val store = LocalTripStore(db, "d", coroutineContext)
        val gap = TripSegment(SegmentKind.GAP, 200.0, 290.0, SegmentReason.TUNNEL, "gap.tunnel")
        repeat(1_000) { i ->
            val id = "t$i"
            store.saveFinalised(FinalisedTrip(trip(id, 1_000L + i, spans = listOf(gap, gap.copy(t0S = 400.0, t1S = 410.0))),
                score = score(id, 80)))
        }
        val trips = LocalTripRepository(db, InMemoryTripFileStore(), coroutineContext)
        val reader = LocalScoreReader(db, coroutineContext)
        trips.observeTrips().first()
        reader.observeLines().first()
        // DRIVE-126 AC2: the list opens in 1 s on a reference phone. Two queries, not 1,001: warm, this is tens of ms.
        val start = System.nanoTime()
        val listed = trips.observeTrips().first()
        val lines = reader.observeLines().first()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(1_000, listed.size)
        assertTrue(listed.all { it.quality?.spans?.size == 2 })
        assertEquals(1_000, lines.lines.size)
        assertTrue(ms < 1_000, "1,000 trips read in $ms ms")
    }

    @Test
    fun theListedTripsCarryTheirSpansFromOneBatchedQuery() = runTest {
        val store = LocalTripStore(db, "d", coroutineContext)
        val gap = TripSegment(SegmentKind.GAP, 200.0, 290.0, SegmentReason.TUNNEL, "gap.tunnel")
        val estimated = TripSegment(SegmentKind.ESTIMATED, 0.0, 20.0, SegmentReason.ESTIMATED_START)
        store.saveFinalised(FinalisedTrip(trip("t1", 1_000L, spans = listOf(estimated, gap))))
        store.saveFinalised(FinalisedTrip(trip("t2", 2_000L)))
        store.saveFinalised(FinalisedTrip(trip("t3", 3_000L, spans = listOf(gap))))

        val trips = LocalTripRepository(db, InMemoryTripFileStore(), coroutineContext).observeTrips().first()

        assertEquals(listOf("t3", "t2", "t1"), trips.map { it.id })
        assertEquals(listOf(gap), trips[0].quality?.spans)
        assertEquals(emptyList(), trips[1].quality?.spans)
        assertEquals(listOf(estimated, gap), trips[2].quality?.spans)
    }
}
