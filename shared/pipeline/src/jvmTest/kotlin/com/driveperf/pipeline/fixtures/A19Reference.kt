package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.trip.EventType
import com.driveperf.foundation.Units
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The A19 reference (DRIVE-93 AC2, one-person run): a second phone fixed in the car is the vehicle-frame reference IMU
 * (field-test-protocol.md "Ground truth and labelling", step 2). Its frame comes from one whole-drive solve: up from
 * the mean specific force, forward from a least-squares fit of the horizontal acceleration to GNSS `dv/dt`. Nothing
 * here uses the pipeline's own alignment, filtering or detector code, so the labels are independent of the candidate.
 *
 * The rubric is A12's (§4.1, frozen on 21 Nov 2026 before any candidate output was seen). A reference peak within
 * [NEAR] of the entry threshold, or a duration, speed or yaw gate within its margin, or a road impact nearby, or GNSS
 * that does not confirm a longitudinal event, makes the label `uncertain`: it counts neither for nor against the app.
 */
internal object A19Reference {
    const val FS = 25.0
    private const val STEP_S = 1.0 / FS
    private const val GRAVITY_WINDOW_S = 60.0
    private const val MERGE_GAP_S = 1.0
    private const val MAX_INTERP_GAP_S = 0.2
    private const val STABLE_WINDOW_S = 10.0
    private const val STABLE_STEP_DEG = 10.0
    private const val MIN_STRETCH_S = 120.0
    private const val SEGMENT_EDGE_S = 10.0
    private const val GAP_GUARD_S = 1.0
    private const val MIN_OK_SHARE = 0.8
    private const val QUIET_G = 0.03
    private const val QUIET_DPS = 3.0
    private const val QUIET_SHARE_INV = 4
    private const val GNSS_PAIR_MAX_S = 2.5
    private const val GNSS_LAG_S = 1.5
    private const val MIN_GNSS_RHO = 0.6

    /** A12 §4.1 entry and exit thresholds in g, minimum duration in s. */
    data class Rule(val type: EventType, val enter: Double, val exit: Double, val minDurS: Double)

    val RULES = listOf(
        Rule(EventType.BRAKE, 0.30, 0.20, 0.40),
        Rule(EventType.ACCEL, 0.28, 0.18, 0.40),
        Rule(EventType.CORNER, 0.36, 0.25, 0.50),
    )
    private const val SPEED_GATE_MPS = 2.5
    private const val YAW_GATE_DPS = 5.0
    private const val NEAR = 0.10
    private const val DUR_MARGIN_S = 0.10
    private const val SPEED_MARGIN_MPS = 0.5
    private const val YAW_MARGIN_DPS = 1.0
    private const val IMPACT_G = 0.40
    private const val IMPACT_PAD = 15
    private const val SAME_EVENT_S = 0.5
    private const val GNSS_DV_SHARE = 0.5

    /** The reference in its vehicle frame on a 25 Hz grid starting at [t0S] (reference trip seconds). */
    @Suppress("LongParameterList") // one array per vehicle-frame channel
    class Frame(
        val t0S: Double,
        val lon: DoubleArray,
        val lat: DoubleArray,
        val vert: DoubleArray,
        val yaw: DoubleArray,
        val speed: DoubleArray,
        val ok: BooleanArray,
        /** Forward-axis correlation with GNSS `dv/dt` at the best lag: kept stretches' mean, else the best stretch's. */
        val gnssRho: Double,
        /** Reference-time spans not covered by a kept stretch: the phone moved, slid or its frame did not solve. */
        val unstable: List<ClosedFloatingPointRange<Double>>,
    ) {
        val n: Int get() = lon.size
        fun tS(k: Int): Double = t0S + k * STEP_S
        val usable: Boolean get() = ok.any { it }
    }

    /** One reference label in reference trip seconds; [why] names the first check an uncertain label failed. */
    data class Label(val type: EventType, val tS: Double, val peakG: Double, val uncertain: Boolean, val why: String? = null)

    // ---- Vehicle frame -------------------------------------------------------------------------------------------

    /**
     * The reference's vehicle frame. The phone is meant to stay put, but it may slide or be moved: the drive is cut into
     * steady stretches (10 s windows whose gravity direction stays within 10° of the stretch's), each ≥ 120 s stretch is
     * solved on its own, and only stretches whose forward axis correlates ≥ 0.6 with GNSS `dv/dt` are kept (`ok`).
     */
    fun frame(motion: List<MotionSample>, fixes: List<LocationSample>, tOf: (Long) -> Double): Frame {
        val t = motion.map { tOf(it.tMonoNs) }.toDoubleArray()
        val t0 = t.first()
        val n = ((t.last() - t0) * FS).toInt() + 1
        fun channel(f: (MotionSample) -> Double) = resample(t, DoubleArray(motion.size) { f(motion[it]) }, t0, n)
        val (fx, okRaw) = channel { it.userAccelG.x + it.gravityUp.x }
        val force = listOf(fx, channel { it.userAccelG.y + it.gravityUp.y }.first, channel { it.userAccelG.z + it.gravityUp.z }.first)
        val gyro = listOf(channel { it.rotRateDps.x }.first, channel { it.rotRateDps.y }.first, channel { it.rotRateDps.z }.first)
        val sensorUp = listOf(channel { it.gravityUp.x }.first, channel { it.gravityUp.y }.first, channel { it.gravityUp.z }.first)
        val speed = speedSeries(fixes, tOf, t0, n)
        val out = Frame(t0, DoubleArray(n), DoubleArray(n), DoubleArray(n), DoubleArray(n), speed, BooleanArray(n), 0.0, emptyList())
        val dvdt = gnssAccel(fixes, tOf)
        val quiet = quietMask(dvdt, gyro, out)
        val kept = mutableListOf<Pair<IntRange, Double>>()
        var bestRho = 0.0
        for (seg in steadyStretches(sensorUp, okRaw)) {
            val rho = solveStretch(seg, force, gyro, okRaw, quiet, dvdt, out)
            bestRho = max(bestRho, rho)
            if (rho >= MIN_GNSS_RHO) kept += seg to rho
        }
        val rho = if (kept.isEmpty()) bestRho else kept.sumOf { (s, r) -> r * s.count() } / kept.sumOf { (s, _) -> s.count() }
        // Covered is every run of valid frame samples: stretch edges are not ok (the centred mean and the filter run out
        // of data there), and neither is an IMU gap over 200 ms, which the filter also smears by up to a guard second.
        val covered = runs(out.ok).map { r -> (out.tS(r.first) + GAP_GUARD_S)..(out.tS(r.last) - GAP_GUARD_S) }
            .filter { it.endInclusive > it.start }
        val unstable = merge(listOf(t0..out.tS(n - 1)).let { all -> subtract(all, covered) })
        return Frame(t0, out.lon, out.lat, out.vert, out.yaw, out.speed, out.ok, rho, unstable)
    }

    /** Index ranges where the phone held still: 10 s windows within 10° of the stretch's first, ≥ 120 s long. */
    private fun steadyStretches(up: List<DoubleArray>, ok: BooleanArray): List<IntRange> {
        val w = (STABLE_WINDOW_S * FS).toInt()
        val sub = FS.toInt()
        fun dir(a: Int, b: Int) = unit(DoubleArray(3) { c -> (a until b).sumOf { up[c][it] } })
        val stretches = mutableListOf<IntRange>()
        var start = -1
        var anchor = doubleArrayOf(0.0, 0.0, 1.0)
        fun close(end: Int) {
            if (start >= 0 && (end - start) >= MIN_STRETCH_S * FS) stretches += start until end
            start = -1
        }
        var a = 0
        while (a + w <= ok.size) {
            val d = dir(a, a + w)
            // A window is steady when it has data and none of its 1 s means leaves its own direction by more than 10°.
            val steady = (a until a + w).count { ok[it] } >= w * MIN_OK_SHARE &&
                (a until a + w step sub).all { s -> angleDeg(dir(s, min(s + sub, a + w)), d) <= STABLE_STEP_DEG }
            when {
                !steady -> close(a)
                start < 0 -> { start = a; anchor = d }
                angleDeg(d, anchor) > STABLE_STEP_DEG -> { close(a); start = a; anchor = d }
            }
            a += w
        }
        close(a)
        return stretches
    }

    /** Solves one steady stretch into [out]; returns its forward-axis correlation with GNSS (written only if ≥ 0.6). */
    @Suppress("LongParameterList") // the stretch's input channels
    private fun solveStretch(
        seg: IntRange,
        force: List<DoubleArray>,
        gyro: List<DoubleArray>,
        okRaw: BooleanArray,
        quiet: BooleanArray,
        dvdt: List<Pair<Double, Double>>,
        out: Frame,
    ): Double {
        val m = seg.count()
        // Gravity as a 60 s centred mean of the specific force over quiet samples (GNSS sees no speed change and the
        // car is not turning), so a long stop or turn does not leak into it; road grade drifts slowly.
        val q = quiet.copyOfRange(seg.first, seg.last + 1)
        val g = force.map { c -> centredMean(c.copyOfRange(seg.first, seg.last + 1), (GRAVITY_WINDOW_S * FS).toInt(), q) }
        val up = unit(doubleArrayOf(g[0].average(), g[1].average(), g[2].average()))
        val kin = Array(m) { i -> DoubleArray(3) { c -> force[c][seg.first + i] - g[c][i] } }
        val e1 = unit(cross(up, if (abs(up[0]) < 0.9) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 1.0, 0.0)))
        val e2 = cross(up, e1)
        val t0 = out.tS(seg.first)
        val inSeg = dvdt.filter { (t, _) -> t >= t0 + 1 && t <= out.tS(seg.last) - 1 }
        val (u, rho) = forward(DoubleArray(m) { dot(kin[it], e1) }, DoubleArray(m) { dot(kin[it], e2) }, inSeg, t0)
        if (rho < MIN_GNSS_RHO) return rho
        val fwd = DoubleArray(3) { u.first * e1[it] + u.second * e2[it] }
        val right = cross(fwd, up)
        val lon = a3(DoubleArray(m) { dot(kin[it], fwd) })
        val lat = a3(DoubleArray(m) { dot(kin[it], right) })
        val yaw = a3(DoubleArray(m) { i -> -(0..2).sumOf { c -> gyro[c][seg.first + i] * up[c] } })
        val edge = (SEGMENT_EDGE_S * FS).toInt()
        for (i in 0 until m) {
            val k = seg.first + i
            out.lon[k] = lon[i]
            out.lat[k] = lat[i]
            out.vert[k] = dot(kin[i], up)
            out.yaw[k] = yaw[i]
            out.ok[k] = okRaw[k] && i >= edge && i < m - edge
        }
        return rho
    }

    fun subtract(
        spans: List<ClosedFloatingPointRange<Double>>,
        holes: List<ClosedFloatingPointRange<Double>>,
    ): List<ClosedFloatingPointRange<Double>> = holes.fold(spans) { acc, h ->
        acc.flatMap { s ->
            if (h.endInclusive <= s.start || h.start >= s.endInclusive) listOf(s)
            else listOfNotNull(
                (s.start..h.start).takeIf { h.start > s.start },
                (h.endInclusive..s.endInclusive).takeIf { h.endInclusive < s.endInclusive },
            )
        }
    }

    private fun angleDeg(a: DoubleArray, b: DoubleArray): Double = Math.toDegrees(acos(dot(a, b).coerceIn(-1.0, 1.0)))

    /** Box average onto the grid (§2.6); empty bins interpolate across ≤ 200 ms, longer gaps are not ok. */
    fun resample(t: DoubleArray, x: DoubleArray, t0: Double, n: Int): Pair<DoubleArray, BooleanArray> {
        val sum = DoubleArray(n)
        val count = IntArray(n)
        for (i in t.indices) {
            val k = ((t[i] - t0) * FS).roundToInt()
            if (k in 0 until n) { sum[k] += x[i]; count[k]++ }
        }
        val out = DoubleArray(n) { if (count[it] > 0) sum[it] / count[it] else Double.NaN }
        val ok = BooleanArray(n) { count[it] > 0 }
        var last = -1
        for (k in 0 until n) {
            if (!ok[k]) continue
            if (last >= 0 && k - last > 1 && (k - last) * STEP_S <= MAX_INTERP_GAP_S) {
                for (j in last + 1 until k) { out[j] = out[last] + (out[k] - out[last]) * (j - last) / (k - last); ok[j] = true }
            }
            last = k
        }
        for (k in 0 until n) if (!ok[k]) out[k] = 0.0
        return out to ok
    }

    /** The `a3` channel (§3): zero-phase forward-backward biquad at 3.0 Hz for fs = 25, steady-state initialised. */
    fun a3(x: DoubleArray): DoubleArray = biquad(biquad(x).reversedArray()).reversedArray()

    private fun biquad(x: DoubleArray): DoubleArray {
        if (x.isEmpty()) return x
        val b0 = 0.09131490043583196
        val b1 = 0.18262980087166392
        val b2 = 0.09131490043583196
        val a1 = -0.9824057931083952
        val a2 = 0.3476653948517232
        var z1 = x[0] * (1 - b0)
        var z2 = x[0] * (b2 - a2)
        return DoubleArray(x.size) { i ->
            val o = b0 * x[i] + z1
            z1 = b1 * x[i] - a1 * o + z2
            z2 = b2 * x[i] - a2 * o
            o
        }
    }

    /** Centred mean over [w] samples, of the [use] samples only when they are at least a quarter of the window. */
    private fun centredMean(x: DoubleArray, w: Int, use: BooleanArray): DoubleArray {
        val all = DoubleArray(x.size + 1)
        val sel = DoubleArray(x.size + 1)
        val cnt = IntArray(x.size + 1)
        for (i in x.indices) {
            all[i + 1] = all[i] + x[i]
            sel[i + 1] = sel[i] + if (use[i]) x[i] else 0.0
            cnt[i + 1] = cnt[i] + if (use[i]) 1 else 0
        }
        return DoubleArray(x.size) { i ->
            val a = max(0, i - w / 2)
            val b = min(x.size, i + w / 2 + 1)
            val k = cnt[b] - cnt[a]
            if (k * QUIET_SHARE_INV >= b - a) (sel[b] - sel[a]) / k else (all[b] - all[a]) / (b - a)
        }
    }

    /** Samples where GNSS sees under 0.03 g of speed change and the phone turns under 3 °/s. */
    private fun quietMask(dvdt: List<Pair<Double, Double>>, gyro: List<DoubleArray>, out: Frame): BooleanArray {
        val a = DoubleArray(out.n) { Double.NaN }
        for ((t, g) in dvdt) {
            val k = ((t - out.t0S) * FS).roundToInt()
            for (j in max(0, k - FS.toInt() / 2) until min(out.n, k + FS.toInt() / 2 + 1)) a[j] = g
        }
        return BooleanArray(out.n) { k ->
            val turn = sqrt(gyro[0][k] * gyro[0][k] + gyro[1][k] * gyro[1][k] + gyro[2][k] * gyro[2][k])
            !a[k].isNaN() && abs(a[k]) < QUIET_G && turn < QUIET_DPS
        }
    }

    private fun speedSeries(fixes: List<LocationSample>, tOf: (Long) -> Double, t0: Double, n: Int): DoubleArray {
        val s = fixes.filter { it.speedMps != null }.map { tOf(it.tMonoNs) to it.speedMps!! }.sortedBy { it.first }
        if (s.isEmpty()) return DoubleArray(n)
        var j = 0
        return DoubleArray(n) { k ->
            val t = t0 + k * STEP_S
            while (j < s.size - 2 && s[j + 1].first <= t) j++
            val (ta, va) = s[j]
            val (tb, vb) = s[min(j + 1, s.size - 1)]
            if (t <= ta || tb <= ta) va else if (t >= tb) vb else va + (vb - va) * (t - ta) / (tb - ta)
        }
    }

    /** GNSS longitudinal acceleration in g at fix times: central differences over fixes ≤ 2.5 s apart. */
    private fun gnssAccel(fixes: List<LocationSample>, tOf: (Long) -> Double): List<Pair<Double, Double>> {
        val s = fixes.filter { it.speedMps != null }.map { tOf(it.tMonoNs) to it.speedMps!! }.sortedBy { it.first }
        return (1 until s.size - 1).mapNotNull { i ->
            val dt = s[i + 1].first - s[i - 1].first
            if (dt <= 0 || dt > GNSS_PAIR_MAX_S) null else s[i].first to (s[i + 1].second - s[i - 1].second) / dt / Units.G
        }
    }

    /** Forward direction in the (e1, e2) plane by least squares against GNSS `dv/dt`, and its best-lag correlation. */
    private fun forward(
        h1: DoubleArray,
        h2: DoubleArray,
        dvdt: List<Pair<Double, Double>>,
        t0: Double,
    ): Pair<Pair<Double, Double>, Double> {
        fun mean(x: DoubleArray, tS: Double): Double? {
            val a = ((tS - 0.5 - t0) * FS).roundToInt()
            val b = ((tS + 0.5 - t0) * FS).roundToInt()
            if (a < 0 || b > x.size) return null
            var sum = 0.0
            for (k in a until b) sum += x[k]
            return sum / (b - a)
        }
        var s1 = 0.0
        var s2 = 0.0
        for ((t, a) in dvdt) {
            val m1 = mean(h1, t)
            val m2 = mean(h2, t)
            if (m1 != null && m2 != null) { s1 += m1 * a; s2 += m2 * a }
        }
        val norm = sqrt(s1 * s1 + s2 * s2)
        if (norm == 0.0) return (1.0 to 0.0) to 0.0
        val u = s1 / norm to s2 / norm
        val lon = DoubleArray(h1.size) { h1[it] * u.first + h2[it] * u.second }
        var best = -1.0
        var lag = -GNSS_LAG_S
        while (lag <= GNSS_LAG_S + 1e-9) {
            val pairs = dvdt.mapNotNull { (t, a) -> mean(lon, t - lag)?.let { it to a } }
            best = max(best, pearson(pairs))
            lag += STEP_S
        }
        return u to best
    }

    // ---- Rubric --------------------------------------------------------------------------------------------------

    private data class Hit(val start: Int, val end: Int, val peak: Int, val p: Double)

    /** Every reference label: the loose pass finds candidates, the exact §4.1 pass with margins decides certainty. */
    fun labels(f: Frame): List<Label> = RULES.flatMap { rule ->
        val signs = if (rule.type == EventType.CORNER) listOf(1, -1) else listOf(if (rule.type == EventType.BRAKE) -1 else 1)
        signs.flatMap { sign ->
            val x = if (rule.type == EventType.CORNER) f.lat else f.lon
            val c = DoubleArray(f.n) { sign * x[it] }
            val loose = detect(c, f.ok, rule.enter * (1 - NEAR), rule.exit * (1 - NEAR)).filter { h ->
                h.durS() >= rule.minDurS - DUR_MARGIN_S && gateSpeed(f, rule, h) >= SPEED_GATE_MPS - SPEED_MARGIN_MPS &&
                    (rule.type != EventType.CORNER || sign * f.yaw[h.peak] >= YAW_GATE_DPS - YAW_MARGIN_DPS)
            }
            val exact = detect(c, f.ok, rule.enter, rule.exit)
            loose.map { h ->
                val match = exact.filter { e -> abs(e.peak - h.peak) * STEP_S <= SAME_EVENT_S }
                val why = if (match.isEmpty()) "below" else match.map { doubt(f, rule, it, sign) }.minByOrNull { it?.length ?: 0 }
                Label(rule.type, f.tS(h.peak), h.p, why != null, why)
            }
        }
    }.sortedBy { it.tS }

    private fun Hit.durS(): Double = (end - start + 1) * STEP_S

    private fun gateSpeed(f: Frame, rule: Rule, h: Hit): Double = if (rule.type == EventType.ACCEL) f.speed[h.end] else f.speed[h.start]

    /** Null when the exact hit clears every margin; otherwise the first check it fails. */
    private fun doubt(f: Frame, rule: Rule, h: Hit, sign: Int): String? = when {
        h.p < rule.enter * (1 + NEAR) -> "peak"
        h.durS() < rule.minDurS + DUR_MARGIN_S -> "duration"
        gateSpeed(f, rule, h) < SPEED_GATE_MPS + SPEED_MARGIN_MPS -> "speed"
        rule.type == EventType.CORNER && sign * f.yaw[h.peak] < YAW_GATE_DPS + YAW_MARGIN_DPS -> "yaw"
        (max(0, h.start - IMPACT_PAD)..min(f.n - 1, h.end + IMPACT_PAD)).any { abs(f.vert[it]) >= IMPACT_G } -> "impact"
        rule.type != EventType.CORNER && !gnssAgrees(f, h, sign) -> "gnss"
        else -> null
    }

    /** §4.4's check the other way round: GNSS must see at least half the speed change the reference IMU claims. */
    private fun gnssAgrees(f: Frame, h: Hit, sign: Int): Boolean {
        val pad = FS.toInt()
        val dv = f.speed[min(f.n - 1, h.end + pad)] - f.speed[max(0, h.start - pad)]
        var imu = 0.0
        for (k in h.start..h.end) imu += f.lon[k] * STEP_S * Units.G
        return sign * dv > 0 && abs(dv) >= GNSS_DV_SHARE * abs(imu)
    }

    /** §4.2 hysteresis on a signed channel, merged across gaps ≤ 1.0 s. */
    private fun detect(c: DoubleArray, ok: BooleanArray, enter: Double, exit: Double): List<Hit> {
        val raw = mutableListOf<IntRange>()
        var k = 0
        while (k < c.size) {
            if (c[k] >= enter && ok[k]) {
                var start = k
                while (start > 0 && c[start - 1] >= exit && ok[start - 1]) start--
                var end = k
                while (end < c.size - 1 && c[end + 1] >= exit && ok[end + 1]) end++
                raw += start..end
                k = end + 1
            } else {
                k++
            }
        }
        val merged = mutableListOf<IntRange>()
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && (r.first - last.last) * STEP_S <= MERGE_GAP_S) merged[merged.size - 1] = last.first..r.last else merged += r
        }
        return merged.map { r ->
            val peak = r.maxBy { c[it] }
            Hit(r.first, r.last, peak, c[peak])
        }
    }

    // ---- Small maths ---------------------------------------------------------------------------------------------

    /** Index runs where [ok] holds. */
    fun runs(ok: BooleanArray): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        for (k in 0..ok.size) {
            val on = k < ok.size && ok[k]
            if (on && start < 0) start = k
            if (!on && start >= 0) { out += start until k; start = -1 }
        }
        return out
    }

    fun merge(spans: List<ClosedFloatingPointRange<Double>>): List<ClosedFloatingPointRange<Double>> {
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        for (s in spans.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && s.start <= last.endInclusive) {
                out[out.size - 1] = last.start..max(last.endInclusive, s.endInclusive)
            } else {
                out += s
            }
        }
        return out
    }

    fun pearson(pairs: List<Pair<Double, Double>>): Double {
        if (pairs.size < 3) return 0.0
        val mx = pairs.sumOf { it.first } / pairs.size
        val my = pairs.sumOf { it.second } / pairs.size
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for ((x, y) in pairs) { sxy += (x - mx) * (y - my); sxx += (x - mx) * (x - mx); syy += (y - my) * (y - my) }
        return if (sxx == 0.0 || syy == 0.0) 0.0 else sxy / sqrt(sxx * syy)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun unit(a: DoubleArray): DoubleArray {
        val n = sqrt(dot(a, a))
        return doubleArrayOf(a[0] / n, a[1] / n, a[2] / n)
    }
}
