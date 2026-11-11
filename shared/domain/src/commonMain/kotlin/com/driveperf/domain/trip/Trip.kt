package com.driveperf.domain.trip

import com.driveperf.domain.capture.WakeKind

/** A half-open span `[t0S, t1S)` in seconds from the trip start (data-model.md §1.8). */
public data class TripSegment(
    val kind: SegmentKind,
    val t0S: Double,
    val t1S: Double,
    val reason: SegmentReason?,
    val labelKey: String? = null,
)

/** Data quality of a trip (data-model.md §1.10, pipeline-and-scoring.md §2.9). */
public data class TripQuality(
    val badge: QualityBadge,
    val reason: QualityReason?,
    val placement: Placement,
    val imuHz: Double,
    val gnssCoveragePct: Double,
    val alignmentSettledS: Double?,
    val estimatedStartM: Double?,
    val scoredS: Double,
    val excludedS: Double,
    val totalS: Double,
    val spans: List<TripSegment>,
)

/** A stop of at least 10 s. Position is null when no gated fix was near. */
public data class Stop(val atS: Double, val durationS: Double, val lat: Double?, val lng: Double?)

/** The cleanest stretch (§6.3), the Story's positive opener. */
public data class Stretch(val distanceM: Double, val t0S: Double, val t1S: Double)

/** Why and when a trip was cut short (F-CAP-06). */
public data class Interruption(val atS: Double, val reason: InterruptionReason)

/** Trip bounds in degrees. */
public data class BoundingBox(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)

/** Capture diagnostics (F-ANA-02 buckets only). Device only, never synced. */
public data class CaptureMeta(
    val wakeKind: WakeKind,
    val startLatencyS: Double?,
    val imuDeliveryPct: Double?,
    val batteryStartPct: Int?,
    val batteryEndPct: Int?,
)

/** One row of the preview track (data-model.md §2.5). [quality] uses the fixture `q` codes: 0 observed, 1 estimated,
 * 2 gap, 3 excluded. */
public data class PreviewRow(
    val tS: Double,
    val lat: Double,
    val lng: Double,
    val speedMps: Double?,
    val lonG: Double?,
    val latG: Double?,
    val elevM: Double?,
    val quality: Int,
)

/**
 * A trip (data-model.md §1.7, plus `suspect` from pipeline-and-scoring.md §0.8). Summary metrics are null while
 * recording and for discarded trips. Times are UTC epoch ms; offsets in seconds are from [startedAt].
 */
public data class Trip(
    val id: String,
    val status: TripStatus,
    val kind: TripKind,
    val source: TripSource,
    val sourceDetail: String? = null,
    val vehicleId: String? = null,
    val startedAt: Long,
    val startTzOffsetMin: Int,
    val startEstimated: Boolean = false,
    val endedAt: Long? = null,
    val endTzOffsetMin: Int? = null,
    val tzId: String? = null,
    val partOfDay: PartOfDay? = null,
    val fromLabel: String? = null,
    val toLabel: String? = null,
    val labelOverride: String? = null,
    val context: DriveContext? = null,
    val distanceM: Double? = null,
    val estimatedDistanceM: Double? = null,
    val durationS: Double? = null,
    val movingS: Double? = null,
    val stoppedS: Double? = null,
    val stopCount: Int? = null,
    val avgMovingSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val maxSpeedAtS: Double? = null,
    val elevGainM: Double? = null,
    val elevLossM: Double? = null,
    val elevMinM: Double? = null,
    val elevMaxM: Double? = null,
    val bbox: BoundingBox? = null,
    val stops: List<Stop> = emptyList(),
    val cleanest: Stretch? = null,
    val quality: TripQuality? = null,
    val interrupted: Interruption? = null,
    val suspect: Suspect = Suspect.NONE,
    val suspectAnswer: SuspectAnswer? = null,
    val captureMeta: CaptureMeta? = null,
    val pipelineVersion: String? = null,
    val rawState: RawState = RawState.FULL,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
