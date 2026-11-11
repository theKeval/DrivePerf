package com.driveperf.pipeline.score

/** R1 `Motion.quality` bits (§2.7). A sample is ok when bits 1–5 are clear. */
public object QualityBits {
    public const val INTERPOLATED: Int = 1
    public const val ESTIMATED: Int = 1 shl 1
    public const val EXCLUDED_PHONE: Int = 1 shl 2
    public const val NOT_ALIGNED: Int = 1 shl 3
    public const val IMU_GAP: Int = 1 shl 4
    public const val LOW_RATE: Int = 1 shl 5
    internal const val NOT_OK: Int = ESTIMATED or EXCLUDED_PHONE or NOT_ALIGNED or IMU_GAP or LOW_RATE
}

/** One gated GNSS fix on the R1 time base (seconds from `startedAt`). */
public data class R1Fix(
    val tS: Double,
    val speedMps: Double,
    val lat: Double? = null,
    val lng: Double? = null,
    val speedAccMps: Double? = null,
)

/**
 * The R1 analysis signal (§0.3): vehicle frame at 25 Hz, plus gated 1 Hz GNSS. Sample `k` is at `t = k / fs`.
 * Longitudinal is positive when accelerating, lateral and yaw positive when turning right (§0.4). Values in g and °/s.
 */
@Suppress("LongParameterList") // one array per R1 channel
public class R1Trace(
    public val lonG: DoubleArray,
    public val latG: DoubleArray,
    public val vertG: DoubleArray,
    public val yawDps: DoubleArray,
    public val quality: IntArray,
    public val gnss: List<R1Fix>,
    public val fs: Int = 25,
) {
    init {
        require(latG.size == lonG.size && vertG.size == lonG.size && yawDps.size == lonG.size && quality.size == lonG.size)
    }

    /** Number of samples. */
    public val size: Int get() = lonG.size

    /** True when sample [k] may be scored (§2.7). */
    public fun ok(k: Int): Boolean = quality[k] and QualityBits.NOT_OK == 0
}
