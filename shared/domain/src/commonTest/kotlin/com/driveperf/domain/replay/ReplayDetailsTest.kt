package com.driveperf.domain.replay

import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.replay.ReplayFixtures.STEP_DEG
import com.driveperf.domain.replay.ReplayFixtures.northbound
import com.driveperf.domain.replay.ReplayFixtures.span
import com.driveperf.domain.replay.ReplayFixtures.trip
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.Stop
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.tripfile.FileResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayDetailsTest {
    private class Trips(
        private val trip: Trip?,
        private val events: List<DrivingEvent> = emptyList(),
        private val feedback: Map<String, EventFeedback> = emptyMap(),
    ) : TripRepository {
        override fun observeTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))

        override suspend fun trip(id: String): Trip? = trip?.takeIf { it.id == id }

        override suspend fun events(tripId: String): List<DrivingEvent> = events

        override suspend fun feedback(tripId: String): Map<String, EventFeedback> = feedback

        override suspend fun score(tripId: String): Score? = null

        override suspend fun delete(tripId: String): FileResult = FileResult.OK
    }

    private class Previews(private val rows: List<PreviewRow>?) : PreviewStore {
        override suspend fun write(tripId: String, rows: List<PreviewRow>): Boolean = true

        override suspend fun read(tripId: String): List<PreviewRow>? = rows
    }

    private fun event(id: String, t: Double, lat: Double? = t * STEP_DEG, type: EventType = EventType.BRAKE) =
        DrivingEvent(id, "trip-1", type, t, t - 1, t + 1, 2.0, 0.4, null, lat, lat?.let { 0.0 }, 12.0, 3.0, Confidence.HIGH, "v1")

    private fun replay(track: List<ReplayPoint> = northbound(0, 100), spans: List<ReplaySpan> = emptyList()) =
        Replay("trip-1", BoundingBox(0.0, 0.0, 0.01, 0.0), track.last().tS, spans, false, track)

    private fun elev(t: Double, m: Double?) = PreviewRow(t, 0.0, 0.0, null, null, null, m, 0)

    @Test
    fun markersLeaveOutInaccurateEventsAndKeepNeededOnesHollow() = runTest {
        val events = listOf(event("b", 50.0), event("a", 20.0), event("c", 70.0))
        val feedback = mapOf(
            "a" to EventFeedback("a", "trip-1", true, null, 1L),
            "c" to EventFeedback("c", "trip-1", false, InaccurateReason.POTHOLE, 1L),
        )
        val repo = TripReplayDetails(Trips(trip(durationS = 100.0), events, feedback), Previews(null))
        val details = assertNotNull(repo.details("trip-1", replay()))
        assertEquals(listOf("a", "b"), details.markers.map { it.eventId })
        assertTrue(details.markers.first().needed)
        assertEquals(1, details.countedEvents)
        assertEquals(setOf("a", "b"), details.events.keys)
        assertEquals("b", details.marker("b")?.eventId)
        assertNull(details.marker("c"))
    }

    @Test
    fun eventsInQualitySpansOrWithoutAPositionAreNotPinned() = runTest {
        val spans = listOf(span(SegmentKind.EXCLUDED, 40.0, 60.0))
        val events = listOf(event("in", 50.0), event("noFix", 30.0, lat = null), event("ok", 80.0))
        val details = assertNotNull(TripReplayDetails(Trips(trip(spans, 100.0), events), Previews(null)).details("trip-1", replay()))
        assertEquals(mapOf("noFix" to false, "in" to false, "ok" to true), details.markers.associate { it.eventId to it.pinned })
    }

    @Test
    fun theMaxSpeedPinIsTheTripsOwnAtTheMarkersPlace() = runTest {
        val trip = trip(durationS = 100.0).copy(maxSpeedMps = 28.0, maxSpeedAtS = 40.0)
        val pin = assertNotNull(TripReplayDetails(Trips(trip), Previews(null)).details("trip-1", replay())?.maxSpeed)
        assertEquals(MaxSpeedPin(40.0, 28.0, 40 * STEP_DEG, 0.0), pin)
    }

    @Test
    fun withoutAStoredTimeTheFastestMeasuredPointIsUsedAndGapsAreSkipped() = runTest {
        val track = northbound(0, 100).map {
            when (it.tS) {
                30.0 -> it.copy(speedMps = 40.0)
                70.0 -> it.copy(speedMps = 90.0)
                else -> it
            }
        }
        val r = replay(track, listOf(ReplaySpan(SegmentStyle.NO_GPS, 65.0, 75.0)))
        val unknown = assertNotNull(TripReplayDetails(Trips(trip(durationS = 100.0)), Previews(null)).details("trip-1", r)?.maxSpeed)
        assertEquals(30.0, unknown.tS)
        assertEquals(40.0, unknown.speedMps)
        val inGap = trip(durationS = 100.0).copy(maxSpeedMps = 33.0, maxSpeedAtS = 70.0)
        val fallback = assertNotNull(TripReplayDetails(Trips(inGap), Previews(null)).details("trip-1", r)?.maxSpeed)
        assertEquals(30.0, fallback.tS, "a stored time in a GPS gap has no place, so the fastest measured point is used")
        assertEquals(33.0, fallback.speedMps)
    }

    @Test
    fun aRouteWithNoSpeedHasNoMaxSpeedPin() = runTest {
        val track = northbound(0, 10).map { it.copy(speedMps = null) }
        assertNull(TripReplayDetails(Trips(trip(durationS = 10.0)), Previews(null)).details("trip-1", replay(track))?.maxSpeed)
    }

    @Test
    fun stopsAreNumberedInTimeOrderAndOnlyPositionedOnesArePinned() = runTest {
        val trip = trip(durationS = 100.0).copy(stops = listOf(Stop(60.0, 12.0, null, null), Stop(20.0, 34.0, 0.002, 0.0)))
        val stops = assertNotNull(TripReplayDetails(Trips(trip), Previews(null)).details("trip-1", replay())).stops
        assertEquals(listOf(1 to 20.0, 2 to 60.0), stops.map { it.number to it.atS })
        assertEquals(listOf(true, false), stops.map { it.pinned })
    }

    @Test
    fun elevationNeedsTwoAltitudesAndCarriesTheTripTotals() = runTest {
        val trip = trip(durationS = 100.0).copy(elevGainM = 18.0, elevLossM = 6.0)
        val rows = listOf(elev(50.0, 120.0), elev(0.0, 100.0), elev(10.0, null), elev(100.0, 110.0))
        val profile = assertNotNull(TripReplayDetails(Trips(trip), Previews(rows)).details("trip-1", replay())?.elevation)
        assertEquals(listOf(0.0, 50.0, 100.0), profile.points.map { it.tS })
        assertEquals(18.0, profile.gainM)
        assertEquals(6.0, profile.lossM)
        assertNull(TripReplayDetails(Trips(trip), Previews(listOf(elev(0.0, 1.0)))).details("trip-1", replay())?.elevation)
        val noTotals = TripReplayDetails(Trips(trip(durationS = 100.0)), Previews(rows)).details("trip-1", replay())?.elevation
        assertEquals(0.0, noTotals?.gainM)
    }

    @Test
    fun aMissingTripHasNoDetails() = runTest {
        assertNull(TripReplayDetails(Trips(null), Previews(null)).details("trip-1", replay()))
        assertNull(NoReplayDetails.details("trip-1", replay()))
    }

    @Test
    fun elevationInterpolatesAndHoldsAtTheEnds() {
        val profile = ElevationProfile(listOf(ElevationPoint(0.0, 100.0), ElevationPoint(10.0, 120.0)), 20.0, 0.0)
        assertEquals(100.0, profile.elevationAt(-5.0))
        assertEquals(110.0, profile.elevationAt(5.0))
        assertEquals(120.0, profile.elevationAt(30.0))
        assertEquals(0.25, profile.heightFractionAt(2.5))
        val flat = ElevationProfile(listOf(ElevationPoint(0.0, 50.0)), 0.0, 0.0)
        assertEquals(0.5, flat.heightFractionAt(3.0))
        assertFailsWith<IllegalArgumentException> { ElevationProfile(emptyList(), 0.0, 0.0) }
    }

    @Test
    fun distanceIsScaledToTheTripsOwnDistance() {
        val track = northbound(0, 10)
        val raw = ReplayDistance(track, null)
        val scaled = ReplayDistance(track, raw.totalM * 2)
        assertEquals(0.0, scaled.at(-1.0))
        assertEquals(raw.totalM * 2, scaled.at(99.0), 1e-9)
        assertEquals(raw.totalM, scaled.at(5.0), 1e-6)
        assertEquals(raw.at(2.5) * 2, scaled.at(2.5), 1e-6)
        assertEquals(0.0, ReplayDistance(emptyList(), 100.0).totalM)
        assertEquals(0.0, ReplayDistance(emptyList(), 100.0).at(3.0))
        assertEquals(raw.totalM, ReplayDistance(track, 0.0).totalM, "a zero trip distance does not scale")
    }

    private fun details(vararg peaks: Double) = ReplayDetails(
        markers = peaks.mapIndexed { i, t -> EventMarker("e$i", EventType.ACCEL, t - 1, t, t + 1, 0.0, 0.0, false, true) },
        events = emptyMap(), maxSpeed = null, stops = emptyList(), elevation = null,
        distance = ReplayDistance(emptyList(), null), startedAtMs = 0L,
    )

    @Test
    fun pinsPulseOnceAsThePlayheadPassesThemGoingForward() {
        val d = details(10.0, 20.0)
        assertEquals(listOf("e0"), d.crossed(9.0, 10.0).map { it.eventId })
        assertTrue(d.crossed(10.0, 11.0).isEmpty(), "already passed")
        assertEquals(listOf("e0", "e1"), d.crossed(5.0, 25.0).map { it.eventId })
        assertTrue(d.crossed(25.0, 5.0).isEmpty(), "backwards")
        assertTrue(d.crossed(0.0, 500.0).isEmpty(), "a jump is a seek")
    }

    @Test
    fun neighboursStepThroughTheMarkersInTimeOrder() {
        val d = details(10.0, 20.0, 30.0)
        assertEquals("e1", d.neighbour("e0", 1)?.eventId)
        assertEquals("e1", d.neighbour("e2", -1)?.eventId)
        assertNull(d.neighbour("e0", -1))
        assertNull(d.neighbour("nope", 1))
        assertFalse(d.markers.isEmpty())
    }

    @Test
    fun staticDetailsAnswerWithWhatTheyHold() = runTest {
        val d = details(1.0)
        assertEquals(d, StaticReplayDetails(d).details("any", replay()))
    }
}
