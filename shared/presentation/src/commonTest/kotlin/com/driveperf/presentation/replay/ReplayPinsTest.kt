package com.driveperf.presentation.replay

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.replay.ElevationPoint
import com.driveperf.domain.replay.ElevationProfile
import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.MaxSpeedPin
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayDetails
import com.driveperf.domain.replay.ReplayDetailsRepository
import com.driveperf.domain.replay.ReplayDistance
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.StaticReplayDetails
import com.driveperf.domain.replay.StaticReplayRepository
import com.driveperf.domain.replay.StopPin
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, setMain, UnconfinedTestDispatcher
class ReplayPinsTest {
    private val track = (0..600 step 10).map { ReplayPoint(it.toDouble(), it * 1e-5, 0.0, 20.0) }
    private val replay = Replay("trip-1", BoundingBox(0.0, 0.0, 0.006, 0.0), 600.0, emptyList(), false, track)

    private fun event(id: String, type: EventType, t: Double) =
        DrivingEvent(id, "trip-1", type, t, t - 1, t + 1, 2.0, 0.4, null, 0.0, 0.0, 12.0, 3.0, Confidence.MEDIUM, "v1")

    private val events = listOf(
        event("e1", EventType.BRAKE, 100.0),
        event("e2", EventType.CORNER, 300.0),
        event("e3", EventType.ACCEL, 400.0),
    )
    private val details = ReplayDetails(
        markers = listOf(
            EventMarker("e1", EventType.BRAKE, 99.0, 100.0, 101.0, 0.001, 0.0, needed = false, pinned = true),
            EventMarker("e2", EventType.CORNER, 299.0, 300.0, 301.0, 0.003, 0.0, needed = true, pinned = true),
            EventMarker("e3", EventType.ACCEL, 399.0, 400.0, 401.0, null, null, needed = false, pinned = false),
        ),
        events = events.associateBy { it.id },
        maxSpeed = MaxSpeedPin(250.0, 28.6, 0.0025, 0.0),
        stops = listOf(StopPin(1, 120.0, 34.0, 0.0012, 0.0), StopPin(2, 500.0, 150.0, null, null)),
        elevation = ElevationProfile(listOf(ElevationPoint(0.0, 100.0), ElevationPoint(600.0, 115.0)), 118.4, 103.0),
        distance = ReplayDistance(track, 39_000.0),
        startedAtMs = 1_000_000L,
    )

    private val tracked = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) {
            tracked += event
        }

        override fun setUsageConsent(allowed: Boolean): Unit = Unit

        override fun resetId(): Unit = Unit
    }
    private var ns = 1_000_000_000L
    private val units = MutableStateFlow(UnitSystem.METRIC)
    private val format = ReplayTimeFormat { "T${it / 1000}" }

    private fun TestScope.presenter(
        source: ReplayDetailsRepository = StaticReplayDetails(details),
    ): Pair<ReplayPresenter, MutableList<ReplayEffect>> {
        val p = ReplayPresenter(
            "trip-1", ReplaySource.TRIP_DETAIL, StaticReplayRepository(replay), { false }, analytics, { ns }, backgroundScope,
            source, units, format,
        )
        val effects = mutableListOf<ReplayEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { p.effects.toList(effects) }
        runCurrent()
        return p to effects
    }

    @Test
    fun theOverlayLoadsWithTheReplay() = runTest {
        val (p, _) = presenter()
        val o = assertNotNull(p.state.value.overlay)
        assertEquals(listOf("e1", "e2"), o.eventPins.map { it.marker.eventId }, "the unpositioned event is not pinned")
        assertEquals(listOf("e1", "e2", "e3"), o.ticks.map { it.marker.eventId }, "every event ticks the timeline")
        assertEquals("Hard braking, T1100. Peak 0.40 g.", o.eventPins[0].accessibilityLabel)
        assertEquals("Hard cornering, marked as needed, T1300. Peak 0.40 g.", o.eventPins[1].accessibilityLabel)
        assertEquals(listOf(1), o.stopPins.map { it.number })
        assertEquals("103 km/h max · 2 stops · 2 events", o.summary)
        assertEquals("↗ 118 m ↘ 103 m", o.elevationText)
        assertNull(o.noEventsText)
        assertEquals("Map of your drive, 39.0 km. 2 events, 2 stops.", o.mapSummary)
        assertEquals("39.0", o.distanceText(600.0))
        assertEquals("km", o.distanceUnit)
        assertEquals("72", o.speedText(20.0))
        assertEquals("—", o.speedText(null))
        assertEquals("km/h", o.speedUnit)
    }

    @Test
    fun theOverlayFollowsTheUnits() = runTest {
        val (p, _) = presenter()
        units.value = UnitSystem.IMPERIAL
        runCurrent()
        val o = assertNotNull(p.state.value.overlay)
        assertEquals("64 mph max · 2 stops · 2 events", o.summary)
        assertEquals("↗ 388 ft ↘ 338 ft", o.elevationText)
        assertEquals("mi", o.distanceUnit)
    }

    @Test
    fun tappingAnEventPausesThereAndOpensTheEvidence() = runTest {
        val (p, effects) = presenter()
        p.dispatch(ReplayIntent.Play)
        ns += 5_000_000_000L
        p.dispatch(ReplayIntent.SelectEvent("e1"))
        val s = p.state.value
        assertFalse(s.playing)
        assertEquals(100.0, s.playheadAt(ns))
        assertEquals(ReplaySelection.Event("e1"), s.selection)
        assertEquals(listOf<ReplayEffect>(ReplayEffect.OpenEvidence("trip-1", "e1")), effects)
        val tap = tracked.last()
        assertEquals("event_pin_tapped", tap.name)
        assertEquals(mapOf("type" to "brake", "confidence" to "medium"), tap.properties)
        p.dispatch(ReplayIntent.ShowEvent("e2"))
        assertEquals(300.0, p.state.value.playheadAt(ns))
        assertEquals(1, effects.size, "stepping from the sheet opens no new sheet")
        p.dispatch(ReplayIntent.SelectEvent("nope"))
        assertEquals(ReplaySelection.Event("e2"), p.state.value.selection)
        p.dispatch(ReplayIntent.ClearSelection)
        assertNull(p.state.value.selection)
    }

    @Test
    fun aStopPinOpensItsSheetAndJumpMovesTheMarker() = runTest {
        val (p, _) = presenter()
        p.dispatch(ReplayIntent.SelectStop(1))
        val sheet = assertNotNull(p.state.value.pinSheet)
        assertEquals(ReplayPinKind.STOP, sheet.kind)
        assertEquals("Stop", sheet.title)
        assertEquals("T1120", sheet.subtitle)
        assertEquals("34" to "s", sheet.value to sheet.unit)
        assertEquals(listOf(ReplayPinRow("Into the drive", "2:00"), ReplayPinRow("Stop", "1 of 2")), sheet.rows)
        assertEquals("Stopped for 34 seconds. Derived.", sheet.accessibilityLabel)
        assertEquals(ReplaySelection.Stop(1), p.state.value.selection)
        assertEquals(mapOf("type" to "stop"), tracked.last().properties)
        assertEquals(0.0, p.state.value.playheadAt(ns), "a stop tap does not move the marker by itself")
        p.dispatch(ReplayIntent.JumpToPin)
        assertEquals(120.0, p.state.value.playheadAt(ns))
        assertNull(p.state.value.pinSheet)
        assertNull(p.state.value.selection)
        p.dispatch(ReplayIntent.JumpToPin)
        assertEquals(120.0, p.state.value.playheadAt(ns), "nothing to jump to")
        p.dispatch(ReplayIntent.SelectStop(9))
        assertNull(p.state.value.pinSheet)
    }

    @Test
    fun aLongStopIsInMinutes() {
        val sheet = ReplayCopy.stopSheet(StopPin(2, 500.0, 150.0, null, null), 2, 0L, ReplayTimeFormat.None)
        assertEquals("3" to "min", sheet.value to sheet.unit)
        assertEquals("", sheet.subtitle)
    }

    @Test
    fun theMaxSpeedPinIsPlainText() = runTest {
        val (p, _) = presenter()
        p.dispatch(ReplayIntent.SelectMaxSpeed)
        val sheet = assertNotNull(p.state.value.pinSheet)
        assertEquals(ReplayPinKind.MAX_SPEED, sheet.kind)
        assertEquals("103" to "km/h", sheet.value to sheet.unit)
        assertEquals("4:10 into the drive · T1250", sheet.subtitle)
        assertEquals("Max speed 103 kilometres per hour. Derived.", sheet.accessibilityLabel)
        assertTrue(sheet.note.contains("never appears on share cards"))
        assertEquals(mapOf("type" to "max_speed"), tracked.last().properties)
    }

    @Test
    fun feedbackReloadsTheDetails() = runTest {
        var calls = 0
        val source = object : ReplayDetailsRepository {
            override suspend fun details(tripId: String, replay: Replay): ReplayDetails? {
                calls++
                val empty = ReplayDetails(emptyList(), emptyMap(), null, emptyList(), null, ReplayDistance(track, null), 0L)
                return if (calls == 1) details else empty
            }
        }
        val (p, _) = presenter(source)
        assertEquals(2, p.state.value.overlay?.eventPins?.size)
        p.dispatch(ReplayIntent.ReloadDetails)
        runCurrent()
        val o = assertNotNull(p.state.value.overlay)
        assertTrue(o.eventPins.isEmpty())
        assertEquals(ReplayCopy.NO_EVENTS, o.noEventsText)
        assertNull(o.elevationText)
        assertEquals("0 stops · 0 events", o.summary)
    }

    @Test
    fun withoutDetailsThereIsNoOverlayAndPinsDoNothing() = runTest {
        val (p, effects) = presenter(com.driveperf.domain.replay.NoReplayDetails)
        assertNotNull(p.state.value.replay)
        assertNull(p.state.value.overlay)
        p.dispatch(ReplayIntent.SelectEvent("e1"))
        p.dispatch(ReplayIntent.SelectStop(1))
        p.dispatch(ReplayIntent.SelectMaxSpeed)
        assertNull(p.state.value.selection)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun theOnMainOverloadLoadsTheDetails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val p = ReplayPresenter.onMain(
                "trip-1", ReplaySource.STORY, StaticReplayRepository(replay), { false }, analytics, { ns },
                StaticReplayDetails(details), units, ReplayTimeFormat.None,
            )
            runCurrent()
            assertEquals("Hard braking. Peak 0.40 g.", p.state.value.overlay?.eventPins?.first()?.accessibilityLabel)
            p.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clocksAndCounts() {
        assertEquals("0:05", ReplayCopy.clock(5.0))
        assertEquals("1:02:05", ReplayCopy.clock(3725.0))
        assertEquals("0:00", ReplayCopy.clock(-3.0))
        assertEquals("1 stop", ReplayCopy.count(1, "stop"))
        assertEquals("a · b", ReplayCopy.dot("a", "", "b"))
        assertEquals("Hard acceleration", ReplayCopy.eventTitle(EventType.ACCEL))
        assertEquals("mph", ReplayCopy.spoken("mph").let { if (it == "miles per hour") "mph" else it })
        assertEquals("", ReplayTimeFormat.None.time(5L))
    }
}
