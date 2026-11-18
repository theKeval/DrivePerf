package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Synthetic coordinates and times: no field data. */
class EstimatedStartTest {
    private val estimator = EstimatedStart(SignalConfig())
    private val first = GatedFix(0.0, 0.0, 0.002, null, 5.0, null, 10.0, 0.5, 90.0, 1.0, false)
    private val header = CaptureHeader(
        "synthetic", 1_000_000_000_000, 1_800_000_000_000, 0, "UTC", WakeKind.MOVE_SIGNAL, null, 50,
        Parking(GeoPoint(0.0, 0.0), 1_799_999_000_000),
    )

    private fun sample(seconds: Double, type: ActivityType, confidence: ActivityConfidence) = ActivitySample(
        header.startMonoNs + (seconds * 1e9).toLong(), header.startWallMs + (seconds * 1000).toLong(), type, confidence,
    )

    private fun estimate(vararg activity: ActivitySample) = estimator.estimate(
        CapturedTrip(header, emptyList(), emptyList(), activity.toList(), emptyList(), emptyList()), listOf(first), 900.0,
    )

    @Test
    fun lowConfidenceAutomotiveContinuesAnEstablishedRun() {
        val result = estimate(
            sample(-300.0, ActivityType.STATIONARY, ActivityConfidence.HIGH),
            sample(-280.0, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH),
            sample(-0.001, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW),
        )
        assertEquals(-280.0, assertNotNull(result).startOffsetS)
    }

    @Test
    fun lowConfidenceAloneDoesNotEstablishARun() {
        assertNull(estimate(sample(-40.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW)))
    }

    @Test
    fun strongerConfidenceEstablishesTheRunWithoutBackdatingThroughLowConfidence() {
        val result = estimate(
            sample(-100.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW),
            sample(-40.0, ActivityType.AUTOMOTIVE, ActivityConfidence.MEDIUM),
        )
        assertEquals(-40.0, assertNotNull(result).startOffsetS)
    }

    @Test
    fun aNonAutomotiveClassificationEndsTheRunEvenAtLowConfidence() {
        assertNull(estimate(
            sample(-100.0, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH),
            sample(-1.0, ActivityType.WALKING, ActivityConfidence.LOW),
        ))
    }

    @Test
    fun automotiveAfterWalkingStartsANewRun() {
        val result = estimate(
            sample(-100.0, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH),
            sample(-50.0, ActivityType.WALKING, ActivityConfidence.HIGH),
            sample(-20.0, ActivityType.AUTOMOTIVE, ActivityConfidence.MEDIUM),
            sample(-1.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW),
        )
        assertEquals(-20.0, assertNotNull(result).startOffsetS)
    }

    private fun fallback(
        fixes: List<GatedFix> = (0..4).map { first.copy(tS = it.toDouble()) },
        history: List<ActivitySample> = emptyList(),
        h: CaptureHeader = header,
        config: SignalConfig = SignalConfig(),
    ) = EstimatedStart(config).estimate(
        CapturedTrip(h, emptyList(), emptyList(), history, emptyList(), emptyList()), fixes, 900.0,
    )

    @Test
    fun missingHistoryUsesMedianEarlySpeedRatherThanAnOutlier() {
        val fixes = listOf(70.0, 10.0, 10.0, 10.0, 10.0).mapIndexed { i, speed ->
            first.copy(tS = i.toDouble(), speedMps = speed)
        }
        val result = assertNotNull(fallback(fixes))
        assertEquals("distance_speed", result.method)
        assertEquals(-result.distanceM / 10.0, result.startOffsetS, 1e-9)
    }

    @Test
    fun evenSpeedSampleCountAveragesTheTwoMiddleValues() {
        val fixes = listOf(30.0, 8.0, 12.0, 0.0).mapIndexed { i, speed -> first.copy(tS = i.toDouble(), speedMps = speed) }
        val result = assertNotNull(fallback(fixes))
        assertEquals(-result.distanceM / 10.0, result.startOffsetS, 1e-9)
    }

    @Test
    fun speedsAfterTheFirst30SecondsDoNotChangeTheEstimate() {
        val fixes = (0..30).map { first.copy(tS = it.toDouble()) } +
            (31..90).map { first.copy(tS = it.toDouble(), speedMps = 50.0) }
        val result = assertNotNull(fallback(fixes))
        assertEquals(-result.distanceM / 10.0, result.startOffsetS, 1e-9)
    }

    @Test
    fun unreliableFixesDoNotSetTheReferenceTimeOrSpeed() {
        val fixes = listOf(
            first.copy(tS = -5.0, hAccM = 25.0), first.copy(tS = -4.0, derived = true),
            first.copy(tS = -3.0, speedAccMps = 3.0), first.copy(tS = -2.0, speedAccMps = null),
        ) + (2..6).map { first.copy(tS = it.toDouble()) }
        val result = assertNotNull(fallback(fixes))
        assertEquals(2.0, result.firstFixOffsetS)
        assertEquals(2.0 - result.distanceM / 10.0, result.startOffsetS, 1e-9)
    }

    @Test
    fun aHistoryRunAtTheWindowEdgeUsesTheFallbackInsteadOfTheEdge() {
        val result = assertNotNull(fallback(history = listOf(sample(-900.0, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH))))
        assertEquals("distance_speed", result.method)
        assertEquals(-result.distanceM / 10.0, result.startOffsetS, 1e-9)
    }

    @Test
    fun motionHistoryWinsOverAPlausibleFallback() {
        val result = assertNotNull(fallback(history = listOf(sample(-40.0, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH))))
        assertEquals("motion_history", result.method)
        assertEquals(-40.0, result.startOffsetS)
    }

    @Test
    fun implausibleHistoryTimeIsRejectedWithoutAFallback() {
        assertNull(fallback(history = listOf(sample(-1.0, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH))))
    }

    @Test
    fun explicitNonAutomotiveHistoryDoesNotUseTheFallback() {
        for (type in listOf(ActivityType.WALKING, ActivityType.RUNNING, ActivityType.CYCLING, ActivityType.STATIONARY)) {
            assertNull(fallback(history = listOf(sample(-1.0, type, ActivityConfidence.HIGH))), type.name)
        }
    }

    @Test
    fun lowConfidenceAutomotiveDoesNotMakeObservedHistoryMissing() {
        val low = sample(-1.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW)
        val fixes = (0..4).map { first.copy(tS = it.toDouble(), lng = 0.009) }
        assertNull(fallback(fixes, history = listOf(low)))
        assertNull(fallback(fixes, history = listOf(
            sample(-60.0, ActivityType.STATIONARY, ActivityConfidence.HIGH), low,
        )), "must not project a 100 s departure through explicit stationary history at -60 s")
    }

    @Test
    fun fallbackBoundsRejectRatherThanClippingDistanceDurationOrSpeed() {
        assertNull(fallback((0..4).map { first.copy(tS = it.toDouble(), lng = 0.05) }), "over 5 km")
        assertNull(fallback((0..4).map { first.copy(tS = it.toDouble(), lng = 0.04, speedMps = 1.0) }), "over 900 s")
        assertNull(fallback((0..4).map { first.copy(tS = it.toDouble(), speedMps = 61.0) }), "over 60 m/s")
        assertNull(fallback((0..4).map { first.copy(tS = it.toDouble(), speedMps = 0.0) }), "zero speed")
        assertNull(fallback((0..4).map { first.copy(tS = it.toDouble(), speedMps = Double.NaN) }), "non-finite speed")
    }

    @Test
    fun fallbackAcceptsValuesAtTheExistingBounds() {
        val base = assertNotNull(fallback())
        assertNotNull(fallback(config = SignalConfig(estimateMaxM = base.distanceM)))
        assertNotNull(fallback(config = SignalConfig(estimateMaxS = base.distanceM / 10.0)))
        assertNotNull(fallback((0..4).map { first.copy(tS = it.toDouble(), speedMps = 60.0) }))
    }

    @Test
    fun fallbackCannotBackdateBeforeParkingOrStartAfterTheWake() {
        assertNull(fallback(h = header.copy(parking = Parking(GeoPoint(0.0, 0.0), header.startWallMs - 1_000))))
        assertNull(fallback((30..34).map { first.copy(tS = it.toDouble()) }))
    }

    @Test
    fun fallbackNeedsEnoughReliableFixesAndAKnownAnchorOnAnAutomaticTrip() {
        assertNull(fallback(listOf(first, first.copy(tS = 1.0))))
        assertNull(fallback((0..4).map { first.copy(tS = it.toDouble(), hAccM = 25.0) }))
        assertNull(fallback(h = header.copy(parking = null)))
        assertNull(fallback(h = header.copy(wakeKind = WakeKind.MANUAL)))
    }

}
