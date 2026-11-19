package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.AnalysisRepository
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, runCurrent
class AnalysisPresenterTest {
    private val events = RecordedAnalytics()
    private val settings = FakeSettings()
    private val units = MutableStateFlow(UnitSystem.METRIC)

    private fun TestScope.presenter(found: TripAnalysis? = AnalysisTestData.analysis()): AnalysisPresenter =
        AnalysisPresenter(
            "t1", AnalysisSource.TRIP_DETAIL, AnalysisRepository { found }, settings, units, events, backgroundScope,
            UnconfinedTestDispatcher(testScheduler),
        ).also { runCurrent() }

    @Test
    fun theStubConstructorHoldsItsTripAndBackClosesTheScreen() = runTest {
        val p = AnalysisPresenter("trip-1", backgroundScope)
        assertEquals("trip-1", p.state.value.tripId)
        p.dispatch(AnalysisIntent.Back)
        assertEquals(AnalysisEffect.Close, p.effects.first())
        p.close()
    }

    @Test
    fun onMainOwnsItsScope() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val p = AnalysisPresenter.onMain("trip-2")
            assertEquals("trip-2", p.state.value.tripId)
            p.close()
            val q = AnalysisPresenter.onMain("trip-3", AnalysisSource.REPLAY, { null }, null, units, null)
            assertEquals("trip-3", q.state.value.tripId)
            q.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadsTheDefaultLanesAndReportsTheOpen() = runTest {
        val s = presenter().state.value
        assertFalse(s.loading)
        assertNull(s.unavailable)
        assertEquals("Preparing 7 min of channels…", s.loadingText)
        val t = assertNotNull(s.timeline)
        assertEquals(listOf(AnalysisChannel.SPEED, AnalysisChannel.LON_G, AnalysisChannel.LAT_G), t.lanes.map { it.channel })
        assertEquals("0–120 km/h", t.lanes[0].rangeLabel)
        assertEquals("±0.6 g", t.lanes[1].rangeLabel)
        assertEquals(0.5, t.lanes[1].zeroY)
        assertNull(t.lanes[0].zeroY)
        assertEquals(10.0 / 400, t.lanes[0].dashedUntilX, 1e-9)
        assertEquals(listOf("brake", "corner"), t.ticks.map { it.eventId })
        assertEquals("Hard braking, 2:30 into the drive.", t.ticks[0].accessibilityLabel)
        assertTrue(t.ticks[1].needed)
        assertEquals(listOf("0:00", "1:40", "3:20", "5:00", "6:40"), t.axisTicks.map { it.label })
        assertEquals(listOf("Speed", "Long G", "Lat G", "Estimated", "No GPS", "Not scored"), t.legend.map { it.label })
        assertEquals("Timeline, 7 minutes. Speed peaked at 36 kilometres per hour. 2 events, the largest 0.31 g.", t.summary)
        assertEquals(listOf("Smoothness", "Max speed", "Events"), s.stats.map { it.title })
        assertEquals("82", s.stats[0].value)
        assertEquals("Based on 6 of 7 min", s.stats[0].detail)
        assertEquals("36 km/h", s.stats[1].value)
        assertEquals("2 on the map", s.stats[2].detail)
        assertNotNull(s.map)
        assertEquals(listOf("analysis_opened"), events.names())
        assertEquals(mapOf("source" to "trip_detail"), events.tracked.single().properties)
    }

    @Test
    fun aMissingTripIsNotFoundAndIgnoresIntents() = runTest {
        val p = presenter(found = null)
        assertEquals(AnalysisUnavailable.NOT_FOUND, p.state.value.unavailable)
        p.dispatch(AnalysisIntent.ZoomIn)
        assertEquals(1.0, p.state.value.zoom)
        assertTrue(events.tracked.isEmpty())
    }

    @Test
    fun theAxisSwitchesToDistanceIsRememberedAndReported() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.SetAxis(AnalysisAxis.DISTANCE))
        runCurrent()
        val t = p.state.value.timeline!!
        assertEquals(AnalysisAxis.DISTANCE, t.axis)
        assertEquals("4.0 km", t.axisTicks.last().label)
        assertEquals(AnalysisAxis.DISTANCE, settings.saved.last().analysisAxis)
        assertEquals(mapOf("axis" to "distance"), events.tracked.last().properties)
        p.dispatch(AnalysisIntent.SetAxis(AnalysisAxis.DISTANCE))
        assertEquals(2, events.tracked.size)
    }

    @Test
    fun theSavedChoicesOpenTheScreen() = runTest {
        settings.update(Settings(analysisAxis = AnalysisAxis.DISTANCE, analysisChannels = setOf(AnalysisChannel.ELEVATION)))
        val t = presenter().state.value.timeline!!
        assertEquals(AnalysisAxis.DISTANCE, t.axis)
        assertEquals(listOf(AnalysisChannel.ELEVATION), t.lanes.map { it.channel })
        assertEquals("96–144 m", t.lanes.single().rangeLabel)
    }

    @Test
    fun zoomStaysBetweenOneAndSixteenAroundTheCursor() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.MoveCursor(300.0))
        p.dispatch(AnalysisIntent.ZoomIn)
        p.dispatch(AnalysisIntent.ZoomIn)
        var s = p.state.value
        assertEquals(4.0, s.zoom)
        assertEquals("4×", s.zoomLabel)
        assertEquals(250.0, s.timeline!!.x0, 1e-9)
        assertEquals(350.0, s.timeline.x1, 1e-9)
        assertEquals(0.5, s.timeline.xOf(300.0), 1e-9)
        repeat(4) { p.dispatch(AnalysisIntent.ZoomIn) }
        assertEquals(16.0, p.state.value.zoom)
        assertFalse(p.state.value.canZoomIn)
        p.dispatch(AnalysisIntent.Pinch(0.75))
        assertEquals("12×", p.state.value.zoomLabel)
        p.dispatch(AnalysisIntent.Pinch(0.9))
        assertEquals("10.8×", p.state.value.zoomLabel)
        p.dispatch(AnalysisIntent.ZoomOut)
        p.dispatch(AnalysisIntent.ResetZoom)
        s = p.state.value
        assertEquals(1.0, s.zoom)
        assertFalse(s.canZoomOut)
        assertEquals(0.0, s.timeline!!.x0)
        p.dispatch(AnalysisIntent.ZoomOut)
        assertEquals(1.0, p.state.value.zoom)
    }

    @Test
    fun theWindowFollowsACursorThatLeavesIt() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.MoveCursor(20.0))
        repeat(3) { p.dispatch(AnalysisIntent.ZoomIn) }
        val before = p.state.value.timeline!!
        assertEquals(0.0, before.x0)
        p.dispatch(AnalysisIntent.MoveCursor(30.0))
        assertTrue(before === p.state.value.timeline)
        p.dispatch(AnalysisIntent.MoveCursor(390.0))
        assertEquals(350.0, p.state.value.timeline!!.x0, 1e-9)
        p.dispatch(AnalysisIntent.MoveCursor(9_999.0))
        assertEquals(400.0, p.state.value.cursorS)
    }

    @Test
    fun channelsToggleArePersistedAndTheLastOneStaysOn() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.ToggleChannel(AnalysisChannel.ELEVATION))
        runCurrent()
        assertTrue(AnalysisChannel.ELEVATION in p.state.value.channels)
        assertEquals(4, p.state.value.timeline!!.lanes.size)
        assertEquals(setOf(AnalysisChannel.SPEED, AnalysisChannel.LON_G, AnalysisChannel.LAT_G, AnalysisChannel.ELEVATION),
            settings.saved.last().analysisChannels)
        assertEquals(mapOf("channel" to "elevation"), events.tracked.last().properties)
        listOf(AnalysisChannel.SPEED, AnalysisChannel.LON_G, AnalysisChannel.LAT_G).forEach {
            p.dispatch(AnalysisIntent.ToggleChannel(it))
        }
        p.dispatch(AnalysisIntent.ToggleChannel(AnalysisChannel.ELEVATION))
        assertEquals(setOf(AnalysisChannel.ELEVATION), p.state.value.channels)
        assertEquals(AnalysisEffect.Message("Keep at least one channel on"), p.effects.first())
        val options = p.state.value.channelOptions
        assertEquals(AnalysisChannel.entries, options.map { it.channel })
        assertTrue(options.first { it.channel == AnalysisChannel.JERK }.pro)
        assertEquals(
            "Lateral G, dashed amber line. Turning left and right. Off.",
            options.first { it.channel == AnalysisChannel.LAT_G }.accessibilityLabel,
        )
    }

    @Test
    fun jerkAndSampleQualityAreLanesToo() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.ToggleChannel(AnalysisChannel.JERK))
        p.dispatch(AnalysisIntent.ToggleChannel(AnalysisChannel.QUALITY))
        val t = p.state.value.timeline!!
        val quality = t.lanes.first { it.channel == AnalysisChannel.QUALITY }
        assertNull(quality.path)
        assertFalse(quality.empty)
        assertEquals("±0.3 g/s", t.lanes.first { it.channel == AnalysisChannel.JERK }.rangeLabel)
        assertEquals(listOf("Speed", "Long G", "Lat G", "Jerk", "Estimated", "No GPS", "Not scored"), t.legend.map { it.label })
        val r = t.readout(210.0)
        assertEquals("Phone moved — not scored", r.values.first { it.channel == AnalysisChannel.QUALITY }.text)
        assertEquals("Observed", t.readout(50.0).values.first { it.channel == AnalysisChannel.QUALITY }.text)
    }

    @Test
    fun theGgCardExpandsFiltersAndJumpsToAPeak() = runTest {
        val p = presenter()
        val gg = p.state.value.gg!!
        assertFalse(gg.empty)
        assertEquals(listOf("0.1 g", "0.2 g", "0.3 g", "0.4 g", "0.5 g"), gg.rings.map { it.label })
        assertTrue(gg.caption!!.endsWith("% of your inputs stayed within 0.2 g"))
        assertEquals("1 min left out: phone moved. Those moments aren’t plotted, so the circle only shows what we could measure fairly.",
            gg.partBanner)
        assertEquals(listOf("Braking", "Cornering"), gg.peaks.map { it.title })
        assertEquals("0.31 g", gg.peaks[0].value)
        assertEquals("1 event · largest 2:30 in", gg.peaks[0].detail)
        assertTrue(gg.accessibilityLabel.startsWith("Friction circle. "))
        assertTrue(gg.accessibilityLabel.endsWith("Peaks: braking 0.31 g, cornering 0.22 g."))
        assertEquals(2, gg.pins.size)
        p.dispatch(AnalysisIntent.ExpandGg)
        p.dispatch(AnalysisIntent.ExpandGg)
        assertTrue(p.state.value.ggExpanded)
        p.dispatch(AnalysisIntent.FilterGg(EventType.CORNER))
        p.dispatch(AnalysisIntent.FilterGg(EventType.CORNER))
        val filtered = p.state.value.gg!!
        assertTrue(filtered.dots.any { !it.emphasised })
        assertEquals(listOf("corner"), filtered.pins.map { it.eventId })
        assertTrue(filtered.filterCaption!!.startsWith("Showing cornering, "))
        assertTrue(filtered.filters.single { it.selected }.type == EventType.CORNER)
        p.dispatch(AnalysisIntent.ShowEvent("brake"))
        assertFalse(p.state.value.ggExpanded)
        assertEquals(150.0, p.state.value.cursorS)
        p.dispatch(AnalysisIntent.ShowEvent("missing"))
        p.dispatch(AnalysisIntent.ExpandGg)
        p.dispatch(AnalysisIntent.CollapseGg)
        assertFalse(p.state.value.ggExpanded)
        assertEquals(listOf("analysis_opened", "gg_expanded", "gg_filter", "gg_expanded"), events.names())
        assertEquals(mapOf("type" to "cornering"), events.tracked[2].properties)
    }

    @Test
    fun replayIsOneEffectAway() = runTest {
        val p = presenter()
        p.dispatch(AnalysisIntent.OpenReplay)
        assertEquals(AnalysisEffect.OpenReplay("t1"), p.effects.first())
    }

    @Test
    fun imperialUnitsRelabelEverything() = runTest {
        val p = presenter(found = AnalysisTestData.analysis(withScore = false))
        units.value = UnitSystem.IMPERIAL
        runCurrent()
        val s = p.state.value
        assertEquals("0–75 mph", s.timeline!!.lanes[0].rangeLabel)
        assertEquals("22 mph", s.stats[1].value)
        assertEquals("—", s.stats[0].value)
        assertEquals("1:40 elapsed · 0.6 mi", s.timeline.readout(100.0).headline)
    }
}
