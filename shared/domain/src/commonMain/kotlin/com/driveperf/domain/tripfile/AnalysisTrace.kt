package com.driveperf.domain.tripfile

import com.driveperf.domain.trip.Trip

/** One gated GNSS fix of the R1 file, on the trip time base (seconds from `Trip.startedAt`). */
public data class AnalysisFix(
    val tS: Double,
    val speedMps: Double,
    val lat: Double?,
    val lng: Double?,
    val speedAccMps: Double?,
)

/**
 * The R1 analysis signal (pipeline-and-scoring.md §0.3, data-model.md §2.4): vehicle frame at [rateHz], sample `k` at
 * `t = k / rateHz` seconds from `Trip.startedAt`, plus the gated 1 Hz fixes. Accelerations in g, yaw rate in °/s;
 * longitudinal positive when accelerating, lateral and yaw positive when turning right. [quality] holds the §2.7 bits.
 * Everything the product shows after finalisation is computable from this alone (§0.2: "R1 is the seam").
 */
@Suppress("LongParameterList") // one array per R1 channel
public class AnalysisTrace(
    public val rateHz: Int,
    public val lonG: DoubleArray,
    public val latG: DoubleArray,
    public val vertG: DoubleArray,
    public val yawDps: DoubleArray,
    public val quality: IntArray,
    public val fixes: List<AnalysisFix>,
) {
    init {
        require(latG.size == lonG.size && vertG.size == lonG.size && yawDps.size == lonG.size && quality.size == lonG.size)
    }

    /** Number of motion samples. */
    public val size: Int get() = lonG.size
}

/**
 * A stored trip file as the `trip_file` table records it (data-model.md §1.17).
 *
 * @property sha256 lower-case hex of the stored (compressed) bytes; this is what Pro backup uploads (ADR-003).
 * @property localPath the path under app-private storage, `trips/<tripId>/<name>`.
 */
public data class TripFileRef(
    val kind: TripFileKind,
    val formatVersion: Int,
    val sha256: String,
    val sizeBytes: Long,
    val localPath: String,
)

/**
 * The R1 analysis file of finalised trips (ADR-003): written once, gzip-compressed and hashed. Implemented in
 * `:shared:data:local` on top of [TripFileStore]; not a platform port.
 */
public interface AnalysisFileStore {
    /** Writes, compresses and describes [trip]'s R1 file. Null when it could not be stored (the trip is kept). */
    public suspend fun write(trip: Trip, trace: AnalysisTrace): TripFileRef?

    /** Reads a trip's R1 file back, or null when it is gone or unreadable ("Detailed trace not kept"). */
    public suspend fun read(tripId: String): AnalysisTrace?
}
