package com.driveperf.domain.analysis

import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A synthetic drive northwards at 10 m/s: no real place, just numbers (rules: no private field data). */
internal object AnalysisFixtures {
    const val RATE = 50

    fun trace(seconds: Int, quality: (Int) -> Int = { 0 }, fixes: List<AnalysisFix>? = null): AnalysisTrace {
        val n = seconds * RATE
        return AnalysisTrace(
            rateHz = RATE,
            lonG = DoubleArray(n) { 0.1 },
            latG = DoubleArray(n) { -0.05 },
            vertG = DoubleArray(n),
            yawDps = DoubleArray(n),
            quality = IntArray(n) { quality(it) },
            fixes = fixes ?: (0 until seconds).map { s -> AnalysisFix(s.toDouble(), 10.0, s * 0.00009, 0.0, 0.5) },
        )
    }

    fun preview(rows: Int, gapAt: Int? = null): List<PreviewRow> = (0 until rows).map { i ->
        PreviewRow(i * 5.0, i * 0.00045, 0.0, 10.0, 0.05, 0.2, 100.0 + i, if (i == gapAt) 2 else 0)
    }
}

class AnalysisDataTest {
    @Test
    fun fromTheTraceEveryChannelIsBuiltOnTheTripTimeBase() {
        val data = AnalysisDataBuilder.build(20.0, null, AnalysisFixtures.trace(20), AnalysisFixtures.preview(4), emptyList())
        assertFalse(data.summaryOnly)
        assertEquals(1000, data.lonG!!.series.size)
        assertEquals(0.1, data.lonG.series.valueAt(3.0))
        assertEquals(-0.05, data.latG!!.series.valueAt(3.0))
        assertEquals(10.0, data.speed!!.series.valueAt(4.5))
        assertEquals(0.0, data.jerk!!.series.valueAt(5.0), 1e-9)
        assertEquals(101.0, data.elevation!!.series.valueAt(5.0))
        assertEquals(data.speed, data.track(AnalysisChannel.SPEED))
        assertEquals(data.lonG, data.track(AnalysisChannel.LON_G))
        assertEquals(data.latG, data.track(AnalysisChannel.LAT_G))
        assertEquals(data.jerk, data.track(AnalysisChannel.JERK))
        assertEquals(data.elevation, data.track(AnalysisChannel.ELEVATION))
        assertNull(data.track(AnalysisChannel.QUALITY))
        assertEquals(data.time, data.axis(AnalysisAxis.TIME))
        assertEquals(data.distance, data.axis(AnalysisAxis.DISTANCE))
        assertTrue(data.distance.length in 180.0..200.0)
        assertEquals(0.0, data.estimatedUntilS)
    }

    @Test
    fun imuGapsBreakTheGLanesButExcludedTimeOnlyLeavesTheCloud() {
        val excluded = ReplaySpan(SegmentStyle.EXCLUDED, 10.0, 15.0)
        val trace = AnalysisFixtures.trace(20, quality = { i -> if (i in 100 until 150) 1 shl 4 else 0 })
        val data = AnalysisDataBuilder.build(20.0, null, trace, emptyList(), listOf(excluded))
        assertTrue(data.lonG!!.series.valueAt(2.5).isNaN())
        assertEquals(0.1, data.lonG.series.valueAt(12.0))
        assertNull(data.gg.dotAt(12.0))
        assertNull(data.gg.dotAt(2.5))
        assertNotNull(data.gg.dotAt(6.0))
        assertNull(data.elevation)
        assertEquals(listOf(excluded), data.bands)
    }

    @Test
    fun speedBreaksAcrossMissingFixesAndNoGpsSpans() {
        val fixes = listOf(0.0, 1.0, 2.0, 8.0, 9.0, 10.0).map { AnalysisFix(it, 12.0, it * 0.0001, 0.0, null) }
        val noGps = ReplaySpan(SegmentStyle.NO_GPS, 9.2, 9.8)
        val data = AnalysisDataBuilder.build(10.0, 500.0, AnalysisFixtures.trace(10, fixes = fixes), emptyList(), listOf(noGps))
        val speed = data.speed!!.series
        assertEquals(12.0, speed.valueAt(1.5))
        assertTrue(speed.valueAt(5.0).isNaN())
        assertTrue(speed.valueAt(9.5).isNaN())
        assertEquals(500.0, data.distance.length, 1e-6)
    }

    @Test
    fun theEstimatedStartIsMarkedForADashedLine() {
        val est = ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 4.0)
        val data = AnalysisDataBuilder.build(20.0, null, AnalysisFixtures.trace(20), emptyList(), listOf(est))
        assertEquals(4.0, data.estimatedUntilS)
    }

    @Test
    fun withoutATraceThePreviewStandsIn() {
        val data = AnalysisDataBuilder.build(20.0, 200.0, null, AnalysisFixtures.preview(5, gapAt = 2), emptyList())
        assertTrue(data.summaryOnly)
        assertNull(data.jerk)
        assertEquals(10.0, data.speed!!.series.valueAt(5.0))
        assertTrue(data.speed.series.valueAt(10.0).isNaN())
        assertEquals(0.05, data.lonG!!.series.valueAt(0.0))
        assertEquals(4, data.gg.moments)
        assertEquals(200.0, data.distance.length, 1e-6)
    }

    @Test
    fun withoutATraceNoGpsSpansStillBreakThePreviewSpeed() {
        // Rows every 5 s, all positioned (no gap row), but no GPS from 6 s to 9 s and from 14 s to 16 s (the 15 s row in it).
        val spans = listOf(ReplaySpan(SegmentStyle.NO_GPS, 6.0, 9.0), ReplaySpan(SegmentStyle.NO_GPS, 14.0, 16.0))
        val speed = AnalysisDataBuilder.build(20.0, null, null, AnalysisFixtures.preview(5), spans).speed!!.series
        assertEquals(10.0, speed.valueAt(2.5))
        assertTrue(speed.valueAt(7.5).isNaN())
        assertEquals(10.0, speed.valueAt(10.0))
        assertTrue(speed.valueAt(15.0).isNaN())
        assertTrue(speed.valueAt(17.0).isNaN())
        assertEquals(10.0, speed.valueAt(20.0))
        val bridged = AnalysisDataBuilder.build(20.0, null, null, AnalysisFixtures.preview(5), emptyList()).speed!!.series
        assertEquals(10.0, bridged.valueAt(7.5))
    }

    @Test
    fun aTripWithNoFixesHasNoSpeedLaneAndAFlatDistanceAxis() {
        val data = AnalysisDataBuilder.build(5.0, null, AnalysisFixtures.trace(5, fixes = emptyList()), emptyList(), emptyList())
        assertNull(data.speed)
        assertEquals(0.0, data.distance.length)
        val empty = AnalysisDataBuilder.build(5.0, null, null, emptyList(), emptyList())
        assertNull(empty.speed)
        assertNull(empty.lonG)
        assertEquals(0, empty.gg.moments)
    }
}
