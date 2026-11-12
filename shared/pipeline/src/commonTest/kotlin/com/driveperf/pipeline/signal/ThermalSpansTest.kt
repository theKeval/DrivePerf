package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.TripSegment
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.ns
import kotlin.test.Test
import kotlin.test.assertEquals

/** §2.7 and F-CAP-16 AC2: a moving 10 Hz span during critical heat is excluded as THERMAL. */
class ThermalSpansTest {
    private val spans = Spans(SignalConfig())

    private fun capture(vararg marks: Pair<Double, String>) = CapturedTrip(
        header(0.0), emptyList(), emptyList(), emptyList(), emptyList(),
        marks.map { (t, detail) -> CaptureMark(ns(t), CaptureMarkType.THERMAL, detail) },
    )

    private fun lowRate(t0: Double, t1: Double) = TripSegment(SegmentKind.EXCLUDED, t0, t1, SegmentReason.LOW_IMU_RATE)

    @Test
    fun lowRateDuringCriticalHeatIsThermal() {
        val c = capture(100.0 to "serious", 200.0 to "critical", 400.0 to "serious")
        val out = spans.relabelThermal(
            listOf(lowRate(50.0, 90.0), lowRate(250.0, 300.0), lowRate(500.0, 520.0), lowRate(390.0, 410.0)),
            c,
            0L,
        )
        assertEquals(
            listOf(SegmentReason.LOW_IMU_RATE, SegmentReason.THERMAL, SegmentReason.LOW_IMU_RATE, SegmentReason.THERMAL),
            out.map { it.reason },
        )
    }

    @Test
    fun heatThatNeverCoolsCoversTheRestAndOtherSpansAreKept() {
        val phone = TripSegment(SegmentKind.EXCLUDED, 300.0, 310.0, SegmentReason.PHONE_MOVED)
        val out = spans.relabelThermal(listOf(lowRate(900.0, 950.0), phone), capture(200.0 to "critical"), 0L)
        assertEquals(listOf(SegmentReason.THERMAL, SegmentReason.PHONE_MOVED), out.map { it.reason })
    }
}
