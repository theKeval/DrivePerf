package com.driveperf.pipeline.score

import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The §8.3 golden table for `smooth-1.0.0`. The table prints one decimal for T and two for R and `S_raw`, so the
 * tolerances are half a printed unit. If a constant changes, regenerate the table in the same change (§8.3).
 */
class GoldenScenariosTest {
    private val analyser = R1Analyser()

    private data class Row(
        val context: DriveContext,
        val t: Double,
        val r: Triple<Double, Double, Double>,
        val wobble: Double,
        val events: Triple<Int, Int, Int>,
        val raw: Double,
        val score: Int,
    )

    private fun check(name: String, trace: R1Trace, row: Row, active: ((R1Analysis) -> List<Int>)? = null): Double {
        val a = analyser.analyse(trace)
        val s = assertNotNull(analyser.score(a, active?.invoke(a) ?: a.events.indices.toList()), name)
        assertEquals(row.context, a.context, "$name context")
        assertEquals(row.t, s.movingS, 0.05, "$name T")
        assertEquals(row.r.first, s.rBrake, 0.005, "$name R brake")
        assertEquals(row.r.second, s.rAccel, 0.005, "$name R accel")
        assertEquals(row.r.third, s.rCorner, 0.005, "$name R corner")
        assertEquals(row.wobble, a.wobbleMps ?: 0.0, 0.005, "$name wobble")
        val counts = Triple(
            a.events.count { it.type == EventType.BRAKE },
            a.events.count { it.type == EventType.ACCEL },
            a.events.count { it.type == EventType.CORNER },
        )
        assertEquals(row.events, counts, "$name events")
        assertEquals(row.raw, s.raw, 0.005, "$name S_raw")
        assertEquals(row.score, s.score, "$name score")
        return s.raw
    }

    @Test
    fun g01Gentle() = check(
        "G-01", Goldens.g01, Row(DriveContext.CITY, 460.2, Triple(0.0, 0.0, 0.0), 0.0, Triple(0, 0, 0), 100.00, 100)).let {}

    @Test
    fun g02Ordinary() = check(
        "G-02", Goldens.g02, Row(DriveContext.CITY, 395.2, Triple(.38, .36, .50), 0.0, Triple(0, 0, 0), 97.22, 97)).let {}

    @Test
    fun g03Brisk() = check(
        "G-03", Goldens.g03, Row(DriveContext.CITY, 351.7, Triple(2.08, 1.74, 1.82), 0.0, Triple(0, 0, 0), 87.83, 88)).let {}

    @Test
    fun g04Rough() {
        check("G-04", Goldens.g04, Row(DriveContext.CITY, 326.7, Triple(10.63, 7.47, 11.28), 0.0, Triple(8, 8, 8), 52.22, 52))
        val first = analyser.analyse(Goldens.g04).events
        fun assertEvent(type: EventType, tPeakS: Double, peakG: Double, confidence: Confidence) {
            val e = first.first { it.type == type }
            assertEquals(tPeakS, e.peak / 25.0, 0.04, "$type tPeak")
            assertEquals(peakG, e.peakG, 0.002, "$type peak")
            assertEquals(confidence, e.confidence, "$type confidence")
        }
        assertEvent(EventType.ACCEL, 3.92, 0.352, Confidence.HIGH)
        assertEvent(EventType.CORNER, 25.36, 0.422, Confidence.MEDIUM)
        assertEvent(EventType.BRAKE, 38.76, 0.452, Confidence.HIGH)
    }

    @Test
    fun g05Highway() = check(
        "G-05", Goldens.g05, Row(DriveContext.HIGHWAY, 601.6, Triple(0.0, 0.0, 0.0), 0.0, Triple(0, 0, 0), 100.00, 100)).let {}

    @Test
    fun g06ACompleteStopOutscoresARollingOne() {
        val complete = check("G-06a", Goldens.g06a,
            Row(DriveContext.CITY, 370.2, Triple(1.39, 1.06, 1.46), 0.0, Triple(0, 0, 0), 91.34, 91))
        val rolling = check("G-06b", Goldens.g06b, Row(DriveContext.CITY, 371.8, Triple(2.74, 1.97, 1.45), 0.0, Triple(0, 0, 0), 86.44, 86))
        assertTrue(complete >= rolling)
    }

    @Test
    fun g07SpeedNeverRaisesTheScore() {
        val city = Row(DriveContext.CITY, 555.5, Triple(.99, .99, .87), 1.18, Triple(0, 0, 0), 89.26, 89)
        val highway = city.copy(context = DriveContext.HIGHWAY, raw = 84.51, score = 85)
        val raws = listOf(6.0 to city, 18.0 to city, 24.0 to highway, 32.0 to highway)
            .map { (v0, row) -> check("G-07 v0=$v0", Goldens.g07(v0), row) }
        assertTrue(raws.zipWithNext().all { (a, b) -> b <= a + 1e-9 }, "S_raw non-increasing as v0 rises: $raws")
    }

    @Test
    fun g08MarkingABrakeNeededRaisesTheScore() {
        val rough = analyser.score(analyser.analyse(Goldens.g04))!!.raw
        val needed = check("G-08", Goldens.g04,
            Row(DriveContext.CITY, 322.6, Triple(9.42, 7.57, 11.42), 0.0, Triple(8, 8, 8), 53.73, 54)) { a ->
            val brake = a.events.indexOfFirst { it.type == EventType.BRAKE }
            assertEquals(38.76, a.events[brake].peak / 25.0, 0.04)
            a.events.indices.filter { it != brake }
        }
        assertTrue(needed > rough)
    }

    @Test
    fun g09ASpeedBumpIsNotAnEvent() =
        check("G-09", Goldens.g09, Row(DriveContext.CITY, 178.5, Triple(0.0, 0.0, 0.0), 0.0, Triple(0, 0, 0), 100.00, 100)).let {}

    @Test
    fun g10ARealPulseIsExactlyOneEvent() =
        check("G-10", Goldens.g10, Row(DriveContext.CITY, 179.9, Triple(3.37, .02, 0.0), 0.0, Triple(1, 0, 0), 91.48, 91)).let {}

    @Test
    fun theSameInputScoresIdenticallyTwice() {
        val a = analyser.analyse(Goldens.g04)
        val b = analyser.analyse(Goldens.g04)
        assertEquals(analyser.score(a), analyser.score(b))
        assertEquals(a.events, b.events)
    }
}
