package com.driveperf.presentation.drives

import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.MaxSpeedPin
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayDetails
import com.driveperf.domain.replay.ReplayDetailsRepository
import com.driveperf.domain.replay.ReplayDistance
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.replay.StopPin
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.score.TripScoreDetail
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.presentation.analysis.MapPin
import com.driveperf.presentation.analysis.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trip detail and the breakdown link to the evidence sheet and the tip (DRIVE-87 AC2, DRIVE-55, DRIVE-102), and the
 * S-TRP-01 map hero (DRIVE-55).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrivesCrossLinksTest {
    private val trips = MutableStateFlow<List<Trip>>(emptyList())
    private val reader = FakeScoreReader()
    private val analytics = RecordingAnalytics()
    private val maps = FakeMaps()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.presenter(maps: TripMapSource? = this@DrivesCrossLinksTest.maps) = DrivesPresenter(
        trips, null, MutableStateFlow(UnitSystem.METRIC), JavaTimeFormatter(), FixedClock(toronto(2026, 11, 10, 18)),
        backgroundScope, reader, analytics, maps = maps,
    )

    private val brake = DrivingEvent("e1", "t", EventType.BRAKE, 120.0, 119.0, 121.4, 1.4, -0.42, null, 43.0, -79.0, 14.0, 0.0,
        Confidence.HIGH, "smooth-1.0.0")

    /** No position: a GPS gap. It has no pin, but its row still opens the evidence. */
    private val unpinned = brake.copy(id = "e2", tPeakS = 600.0, lat = null, lng = null)
    private val corner = brake.copy(id = "e3", type = EventType.CORNER, tPeakS = 300.0, peakG = 0.4)

    private fun score(tipEvent: String? = "e1") = Score(
        "t", 85, 85.0, 83, 2, ScoreBasis.ROUTE, "r1", null,
        listOf(
            Contributor(ContributorId.BRAKING, 70.0, 1.0, Band.ROUGHER, "braking.rougher", listOf("e2", "e1")),
            Contributor(ContributorId.ACCELERATION, 95.0, 0.1, Band.SMOOTHER, "acceleration.smoother", emptyList()),
            Contributor(ContributorId.CORNERING, 88.0, 0.4, Band.USUAL, "cornering.usual", listOf("e3")),
            Contributor(ContributorId.CONSISTENCY, 90.0, 0.2, Band.SMOOTHER, "consistency.smoother", emptyList()),
        ),
        null, null, Tip("brake_light", TipCategory.BRAKING, tipEvent, "p1"), null, "smooth-1.0.0", 1L,
    )

    private fun scored(t: Trip, s: Score = score(), feedback: Map<String, EventFeedback> = emptyMap()) {
        reader.details.value += t.id to TripScoreDetail(t.id, s, listOf(brake, unpinned, corner).map { it.copy(tripId = t.id) },
            feedback, RouteName("r1", null, RoutePreset.COMMUTE), null, "GTI", emptyList())
    }

    private fun inaccurate(id: String) = EventFeedback(id, "t", false, InaccurateReason.PHONE_MOVED, 1L)

    @Test
    fun `event rows open the evidence, pinned or not, but not an event reported not accurate`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        scored(t, feedback = mapOf("e3" to inaccurate("e3")))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        p.dispatch(DrivesIntent.OpenEvidence("t", "e2"))
        assertEquals(DrivesEffect.OpenEvidence("t", "e2"), p.effects.first())
        p.dispatch(DrivesIntent.OpenEvidence("t", "e3"))
        p.dispatch(DrivesIntent.OpenEvidence("t", "e1"))
        assertEquals(DrivesEffect.OpenEvidence("t", "e1"), p.effects.first())
    }

    @Test
    fun `a contributor's events on the breakdown open the evidence`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        scored(t)
        val p = presenter()
        p.dispatch(DrivesIntent.ShowBreakdown("t"))
        val braking = p.state.value.detail!!.score!!.contributors.single { it.id == ContributorId.BRAKING }
        assertEquals(listOf("e2", "e1"), braking.events.map { it.id })
        p.dispatch(DrivesIntent.TapContributor(ContributorId.BRAKING))
        p.dispatch(DrivesIntent.OpenEvidence("t", braking.events.first().id))
        assertEquals(DrivesEffect.OpenEvidence("t", "e2"), p.effects.first())
        assertEquals(listOf(mapOf("contributor" to "braking")), analytics.named("contributor_tapped"))
    }

    @Test
    fun `the tip card opens S-SCO-04 and its moment opens the evidence with tip_evidence_opened`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        scored(t)
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        assertEquals("e1", p.state.value.detail!!.tip!!.eventId)
        p.dispatch(DrivesIntent.OpenTip("t"))
        assertEquals(DrivesEffect.OpenTip("t"), p.effects.first())
        assertTrue(analytics.named("tip_evidence_opened").isEmpty())
        p.dispatch(DrivesIntent.OpenTipEvidence("t"))
        assertEquals(DrivesEffect.OpenEvidence("t", "e1"), p.effects.first())
        assertEquals(listOf(mapOf("tip_id" to "brake_light")), analytics.named("tip_evidence_opened"))
    }

    @Test
    fun `a tip without a moment to see has no evidence link`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        scored(t, feedback = mapOf("e1" to inaccurate("e1")))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        assertNull(p.state.value.detail!!.tip!!.eventId)
        p.dispatch(DrivesIntent.OpenTipEvidence("t"))
        scored(t, score(tipEvent = null))
        assertNull(p.state.value.detail!!.tip!!.eventId)
        p.dispatch(DrivesIntent.OpenTipEvidence("t"))
        // Another trip's tip is never opened from this one.
        p.dispatch(DrivesIntent.OpenTipEvidence("other"))
        p.dispatch(DrivesIntent.OpenTip("t"))
        assertEquals(DrivesEffect.OpenTip("t"), p.effects.first())
        assertTrue(analytics.named("tip_evidence_opened").isEmpty())
    }

    @Test
    fun `the map hero loads with the detail, labels itself and reloads when feedback changes`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD), estimatedM = 400.0)
        trips.value = listOf(t)
        scored(t)
        val p = presenter()
        assertNull(p.state.value.detail)
        p.dispatch(DrivesIntent.Open("t"))
        val map = assertNotNull(p.state.value.detail!!.map)
        assertEquals("Map of your drive, 39.0 km. 2 events, 1 stop.", map.accessibilityLabel)
        assertTrue(map.estimatedStart)
        assertEquals(listOf("t"), maps.loaded)
        scored(t, feedback = mapOf("e1" to inaccurate("e1")))
        assertEquals(listOf("t", "t"), maps.loaded)
        p.dispatch(DrivesIntent.CloseDetail)
        assertNull(p.state.value.detail)
    }

    @Test
    fun `the map hero shows no pins while the scores are hidden`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD), kind = TripKind.PASSENGER)
        trips.value = listOf(t)
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        val map = assertNotNull(p.state.value.detail!!.map)
        assertTrue(map.pins.isEmpty())
        assertFalse(map.estimatedStart)
        assertEquals("Map of your drive, 39.0 km. 0 events, 1 stop.", map.accessibilityLabel)
    }

    @Test
    fun `without a map source or a route there is no hero`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        val p = presenter(maps = null)
        p.dispatch(DrivesIntent.Open("t"))
        assertNull(p.state.value.detail!!.map)
        val q = presenter(maps = TripMapSource { null })
        q.dispatch(DrivesIntent.Open("t"))
        assertNull(q.state.value.detail!!.map)
    }

    @Test
    fun `TripMaps projects the route, the pinned events and stops, and the max speed`() = runTest(UnconfinedTestDispatcher()) {
        // A synthetic route due north along 0° (no real place).
        val track = (0..10).map { ReplayPoint(it * 10.0, it * 0.001, 0.0, 10.0) }
        val replay = Replay("t", BoundingBox(0.0, 0.0, 0.01, 0.0), 100.0, listOf(ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 20.0)),
            false, track)
        val markers = listOf(
            EventMarker("e1", EventType.BRAKE, 49.0, 50.0, 51.0, 0.005, 0.0, needed = false, pinned = true),
            EventMarker("e2", EventType.BRAKE, 59.0, 60.0, 61.0, null, null, needed = false, pinned = false),
        )
        val details = ReplayDetails(
            markers, emptyMap(), MaxSpeedPin(90.0, 12.0, 0.009, 0.0),
            listOf(StopPin(1, 30.0, 20.0, 0.003, 0.0), StopPin(2, 70.0, 15.0, null, null)),
            null, ReplayDistance(track, null), 0L,
        )
        val source = TripMaps(FixedReplay(replay), FixedDetails(details), UnconfinedTestDispatcher(testScheduler))
        val map = assertNotNull(source.map("t"))
        assertEquals(0.0, map.widthM, 1e-6)
        assertEquals(1_111.95, map.heightM, 0.1)
        assertEquals(listOf(SegmentStyle.ESTIMATED, SegmentStyle.OBSERVED), map.segments.map { it.style })
        assertEquals(listOf("e1"), map.pins.map { it.eventId })
        assertEquals(555.97, map.pins.single().at.y, 0.1)
        assertEquals(1, map.stops.size)
        assertEquals(1_000.76, assertNotNull(map.maxSpeed).y, 0.1)
        assertNull(TripMaps(FixedReplay(null), FixedDetails(details)).map("t"))
        val point = Replay("t", BoundingBox(0.0, 0.0, 0.0, 0.0), 0.0, emptyList(), false, track.take(1))
        assertNull(TripMaps(FixedReplay(point), FixedDetails(details)).map("t"))
        val bare = assertNotNull(TripMaps(FixedReplay(replay), FixedDetails(null), UnconfinedTestDispatcher(testScheduler)).map("t"))
        assertTrue(bare.pins.isEmpty() && bare.stops.isEmpty() && bare.maxSpeed == null)
    }

    @Test
    fun `the map label counts in words`() {
        assertEquals("Map of your drive, 1.2 km. 1 event, 0 stops.", tripMapLabel("1.2 km", 1, 0))
    }

    @Test
    fun `onMain with a map source owns its scope`() = runTest(UnconfinedTestDispatcher()) {
        val p = DrivesPresenter.onMain(trips, null, MutableStateFlow(UnitSystem.METRIC), JavaTimeFormatter(),
            FixedClock(0L), reader, analytics, maps = maps)
        trips.value = listOf(trip())
        p.dispatch(DrivesIntent.Open("t"))
        assertNotNull(p.state.value.detail?.map)
        p.close()
    }
}

/** A map source that answers with one fixed hero and records which trips it loaded. */
private class FakeMaps : TripMapSource {
    val loaded = mutableListOf<String>()

    override suspend fun map(tripId: String): TripMapUi {
        loaded += tripId
        return TripMapUi(
            widthM = 100.0,
            heightM = 200.0,
            segments = emptyList(),
            pins = listOf(
                MapPin("e1", EventType.BRAKE, false, MapPoint(1.0, 2.0)),
                MapPin("e3", EventType.CORNER, false, MapPoint(3.0, 4.0)),
            ),
            stops = listOf(MapPoint(5.0, 6.0)),
            maxSpeed = null,
        )
    }
}

private class FixedDetails(private val details: ReplayDetails?) : ReplayDetailsRepository {
    override suspend fun details(tripId: String, replay: Replay): ReplayDetails? = details
}

private class FixedReplay(private val replay: Replay?) : ReplayRepository {
    override suspend fun replay(tripId: String): Replay? = replay
}
