package com.driveperf.domain.replay

import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.SegmentKind
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus

/** Synthetic tracks for the Replay tests. Positions are made up near 0°, 0°; no real location data. */
internal object ReplayFixtures {
    /** About 11 m of latitude: a fix every second at roughly 40 km/h. */
    const val STEP_DEG = 1e-4

    @Suppress("LongParameterList") // one knob per trip property the tests vary
    fun trip(
        spans: List<TripSegment> = emptyList(),
        durationS: Double? = null,
        bbox: BoundingBox? = null,
        source: TripSource = TripSource.AUTO,
        status: TripStatus = TripStatus.FINALISED,
        rawState: RawState = RawState.FULL,
        id: String = "trip-1",
    ): Trip = Trip(
        id = id,
        status = status,
        kind = TripKind.DRIVER,
        source = source,
        startedAt = 1_000_000L,
        startTzOffsetMin = 0,
        startEstimated = spans.any { it.kind == SegmentKind.ESTIMATED },
        durationS = durationS,
        bbox = bbox,
        quality = TripQuality(
            badge = QualityBadge.GOOD,
            reason = null,
            placement = Placement.MOUNTED,
            imuHz = 50.0,
            gnssCoveragePct = 100.0,
            alignmentSettledS = 10.0,
            estimatedStartM = null,
            scoredS = 0.0,
            excludedS = 0.0,
            totalS = durationS ?: 0.0,
            spans = spans,
        ),
        rawState = rawState,
        createdAt = 0L,
        updatedAt = 0L,
    )

    /** One fix per second from [fromS] to [toS] inclusive, heading north at [STEP_DEG] per second. */
    fun northbound(fromS: Int, toS: Int, speedMps: Double = 11.0): List<ReplayPoint> =
        (fromS..toS).map { ReplayPoint(it.toDouble(), it * STEP_DEG, 0.0, speedMps) }

    fun span(kind: SegmentKind, t0: Double, t1: Double, reason: SegmentReason? = null): TripSegment =
        TripSegment(kind, t0, t1, reason)

    val parking: ReplayPoint = ReplayPoint(0.0, -0.002, -0.001, null)
}
