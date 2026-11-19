package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.settings.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class AnalysisTimelineTest {
    private val analysis = AnalysisTestData.analysis()
    private val map = AnalysisMap.of(analysis)

    private fun timeline(
        x0: Double = 0.0,
        x1: Double = 400.0,
        axis: AnalysisAxis = AnalysisAxis.TIME,
        channels: Set<AnalysisChannel> = AnalysisChannel.DEFAULTS + AnalysisChannel.ELEVATION,
    ) = AnalysisTimeline.build(analysis, map, axis, x0, x1, channels, UnitSystem.METRIC)

    @Test
    fun theReadoutCarriesEveryLaneTheMarkerAndTheDot() {
        val r = timeline().readout(50.0)
        assertEquals("0:50 elapsed · 0.5 km", r.headline)
        assertNull(r.chip)
        assertEquals(listOf("36 km/h", "0.00 g", "−0.17 g", "105 m"), r.values.map { it.text })
        assertEquals(0.125, r.x)
        assertTrue(r.values.all { !it.y.isNaN() })
        val marker = assertNotNull(r.marker)
        assertEquals(500.0, marker.y, 5.0)
        assertNotNull(r.gg)
        assertEquals("0:50 elapsed, 0.5 km. Speed 36 km/h. Long G 0.00 g. Lat G −0.17 g. Elevation 105 m", r.accessibilityValue)
    }

    @Test
    fun inAGpsGapSpeedReadsADashAndTheMarkerHides() {
        val r = timeline().readout(115.0)
        assertEquals("No GPS", r.chip)
        assertEquals("—", r.values.first { it.channel == AnalysisChannel.SPEED }.text)
        assertTrue(r.values.first { it.channel == AnalysisChannel.SPEED }.y.isNaN())
        assertTrue(r.values.first { it.channel == AnalysisChannel.LON_G }.text != "—")
        assertNull(r.marker)
    }

    @Test
    fun excludedTimeKeepsItsLinesButLeavesTheCloud() {
        val r = timeline().readout(230.0)
        assertEquals("Phone moved — not scored", r.chip)
        assertNull(r.gg)
        assertEquals("Estimated start", timeline().readout(5.0).chip)
    }

    @Test
    fun bandsAndTicksAreClippedToTheWindow() {
        val t = timeline(x0 = 90.0, x1 = 190.0)
        assertEquals(listOf(SegmentStyle.NO_GPS), t.bands.map { it.style })
        assertEquals(0.1, t.bands.single().x0, 1e-9)
        assertEquals(0.4, t.bands.single().x1, 1e-9)
        assertEquals(listOf("brake"), t.ticks.map { it.eventId })
        assertEquals(0.6, t.ticks.single().x, 1e-9)
        assertEquals(0.0, t.lanes[0].dashedUntilX)
    }

    @Test
    fun aDragSnapsToANearbyTickOnly() {
        val t = timeline()
        assertEquals(CursorSnap(150.0, "brake"), t.snap(0.38, 0.02))
        val free = t.snap(0.5, 0.02)
        assertNull(free.eventId)
        assertEquals(200.0, free.tS, 1e-9)
        assertEquals(400.0, t.timeAt(2.0))
    }

    @Test
    fun theDistanceAxisLabelsKilometresAndKeepsTicksAligned() {
        val t = timeline(x0 = 0.0, x1 = 4_000.0, axis = AnalysisAxis.DISTANCE)
        assertEquals("2.0 km", t.axisTicks[2].label)
        assertEquals(t.xOf(150.0), t.ticks.first().x, 1e-9)
        assertEquals(150.0, t.timeAt(t.xOf(150.0)), 1e-6)
    }

    @Test
    fun theTableHasARowEveryThirtySecondsAndOneForEachEvent() {
        val rows = timeline().tableRows()
        assertEquals(14 + 2, rows.size)
        assertEquals("0:00", rows.first().time)
        val brake = rows.first { it.note != null }
        assertEquals("2:30", brake.time)
        assertEquals("Hard braking", brake.note)
        assertEquals("—", rows.first { it.time == "2:00" }.speed)
    }

    @Test
    fun aDegenerateWindowFallsBackToOneUnit() {
        val t = timeline(x0 = 0.0, x1 = 0.0)
        assertEquals(1.0, t.x1)
    }

    @Test
    fun theMapProjectsTheRouteAndSnapsATapToTheNearestPoint() {
        val m = assertNotNull(map)
        assertEquals(0.0, m.widthM)
        assertEquals(4_000.0, m.heightM, 5.0)
        assertTrue(m.segments.isNotEmpty())
        assertEquals(listOf("brake", "corner"), m.pins.map { it.eventId })
        assertEquals(200.0, m.nearestTime(0.0, 2_001.0), 1.0)
        assertNull(AnalysisMap.of(AnalysisTestData.analysis().let {
            com.driveperf.domain.analysis.TripAnalysis(it.trip, null, it.data, it.markers, it.events, it.score)
        }))
    }

    /**
     * S-ANL-01 performance (DRIVE-45 done criteria): an hour of 50 Hz data. Building the channels happens once, off the
     * main thread; every zoom or channel change then rebuilds a bounded number of points, and a cursor frame is a few
     * lookups. The budgets are loose so CI never flakes; the point counts are the real guarantee.
     */
    @Test
    fun anHourOfFiftyHertzStaysSmooth() {
        val hour = AnalysisTestData.analysis(seconds = 3_600)
        val all = AnalysisChannel.entries.toSet()
        lateinit var full: AnalysisTimeline
        val rebuild = measureTime {
            full = AnalysisTimeline.build(hour, AnalysisMap.of(hour), AnalysisAxis.TIME, 0.0, 3_600.0, all, UnitSystem.METRIC)
            AnalysisTimeline.build(hour, null, AnalysisAxis.TIME, 1_800.0, 2_025.0, all, UnitSystem.METRIC)
            AnalysisTimeline.build(hour, null, AnalysisAxis.DISTANCE, 0.0, hour.data.distance.length, all, UnitSystem.METRIC)
        }
        full.lanes.mapNotNull { it.path }.forEach { assertTrue(it.size <= 3 * AnalysisTimeline.BUCKETS, "${it.size} points") }
        val frames = measureTime { repeat(600) { full.readout(full.timeAt(it / 600.0)) } }
        println("Analysis perf (1 h @ 50 Hz): 3 rebuilds $rebuild, 600 cursor frames $frames")
        assertTrue(frames.inWholeMilliseconds < 2_000, "cursor frames took $frames")
    }
}
