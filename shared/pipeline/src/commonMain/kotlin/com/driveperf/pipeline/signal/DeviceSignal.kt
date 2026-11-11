package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.MotionSample
import com.driveperf.pipeline.score.QualityBits

/**
 * The R0 IMU resampled to 25 Hz in the device frame (§2.6): box mean over `[t − 20 ms, t + 20 ms)`, linear
 * interpolation across ≤ 200 ms (INTERPOLATED), otherwise IMU_GAP and zeros. LOW_RATE where the delivered rate in
 * that second is under 20 Hz.
 */
internal class DeviceSignal(
    val accel: Array<V3>,
    val gravity: Array<V3>,
    val gyro: Array<V3>,
    val quality: IntArray,
    /** Delivered samples per whole second, for the badge's IMU delivery and the median rate. */
    val perSecond: IntArray,
) {
    val size: Int get() = accel.size

    companion object {
        private const val NS = 1e9

        /** Resamples [motion] onto `t_k = k / fs` for `k` in `[0, n)`, where `t` is seconds from [t0Ns]. */
        @Suppress("CyclomaticComplexMethod", "LongMethod")
        fun resample(motion: List<MotionSample>, t0Ns: Long, n: Int, cfg: SignalConfig): DeviceSignal {
            val dt = 1.0 / cfg.fs
            val half = dt / 2
            val times = DoubleArray(motion.size) { (motion[it].tMonoNs - t0Ns) / NS }
            val accel = Array(n) { V3.ZERO }
            val gravity = Array(n) { V3.ZERO }
            val gyro = Array(n) { V3.ZERO }
            val quality = IntArray(n)
            val seconds = (n * dt).toInt() + 1
            val perSecond = IntArray(seconds)
            for (t in times) if (t >= 0) { val s = t.toInt(); if (s < seconds) perSecond[s]++ }
            var j = 0
            for (k in 0 until n) {
                val tk = k * dt
                while (j < times.size && times[j] < tk - half) j++
                var m = j
                var sa = V3.ZERO
                var sg = V3.ZERO
                var sr = V3.ZERO
                var count = 0
                while (m < times.size && times[m] < tk + half) {
                    sa += motion[m].userAccelG.v3()
                    sg += motion[m].gravityUp.v3()
                    sr += motion[m].rotRateDps.v3()
                    count++
                    m++
                }
                if (count > 0) {
                    val inv = 1.0 / count
                    accel[k] = sa * inv
                    gravity[k] = sg * inv
                    gyro[k] = sr * inv
                } else {
                    val before = j - 1
                    val after = m
                    val canInterpolate = before >= 0 && after < times.size && times[after] - times[before] <= cfg.interpolateMaxS
                    if (canInterpolate) {
                        val f = (tk - times[before]) / (times[after] - times[before])
                        accel[k] = lerp(motion[before].userAccelG.v3(), motion[after].userAccelG.v3(), f)
                        gravity[k] = lerp(motion[before].gravityUp.v3(), motion[after].gravityUp.v3(), f)
                        gyro[k] = lerp(motion[before].rotRateDps.v3(), motion[after].rotRateDps.v3(), f)
                        quality[k] = quality[k] or QualityBits.INTERPOLATED
                    } else {
                        quality[k] = quality[k] or QualityBits.IMU_GAP
                    }
                }
                val second = tk.toInt()
                if (second < seconds && perSecond[second] < cfg.lowRateHz && quality[k] and QualityBits.IMU_GAP == 0) {
                    quality[k] = quality[k] or QualityBits.LOW_RATE
                }
            }
            return DeviceSignal(accel, gravity, gyro, quality, perSecond)
        }

        private fun lerp(a: V3, b: V3, f: Double): V3 = a + (b - a) * f
    }
}

internal fun com.driveperf.domain.capture.Vec3.v3(): V3 = V3(x, y, z)
