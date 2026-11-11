package com.driveperf.pipeline.score

/**
 * A second-order section with the §3 coefficients for `fs = 25`. [filtfilt] runs it forward and backward
 * (zero phase), each pass initialised to the steady state of its first sample.
 */
internal class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    fun filtfilt(x: DoubleArray): DoubleArray = if (x.isEmpty()) x else pass(pass(x).reversedArray()).reversedArray()

    private fun pass(x: DoubleArray): DoubleArray {
        val out = DoubleArray(x.size)
        var z1 = x[0] * (1 - b0)
        var z2 = x[0] * (b2 - a2)
        for (i in x.indices) {
            val o = b0 * x[i] + z1
            z1 = b1 * x[i] - a1 * o + z2
            z2 = b2 * x[i] - a2 * o
            out[i] = o
        }
        return out
    }

    companion object {
        /** `a3`: 3.0 Hz, for detectors, displayed G, G-G and peak G. */
        val LOWPASS_3HZ: Biquad = Biquad(
            0.09131490043583196, 0.18262980087166392, 0.09131490043583196, -0.9824057931083952, 0.3476653948517232,
        )

        /** `a1.5`: 1.5 Hz, for the jerk ledger. */
        val LOWPASS_1_5HZ: Biquad = Biquad(
            0.027859766117136024, 0.05571953223427205, 0.027859766117136024, -1.475480443592646, 0.5869195080611902,
        )
    }
}
