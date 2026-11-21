package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.pipeline.score.Coverage
import com.driveperf.pipeline.score.EventHarness
import com.driveperf.pipeline.score.LabelledEvent
import com.driveperf.testing.FixtureCsv
import com.driveperf.testing.FixtureCsv.fixed
import com.driveperf.testing.SyntheticCapture
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns one A19 paired drive into a labelled set entry (DRIVE-93 AC2): the candidate phone's private R0 export becomes
 * a privacy-trimmed §8.2 fixture, and the fixed reference phone's export becomes its `<id>.labels.csv` through
 * [A19Reference]. Only the candidate's replay time base is read from the pipeline; its events are never looked at here.
 */
internal object A19Labeller {
    private const val MIN_OVERLAP_S = 300.0
    private const val SYNC_SEARCH_S = 10.0
    private const val YAW_CLIP_DPS = 60.0
    private const val MIN_SYNC_SAMPLES = 1500
    private const val MIN_SYNC_RHO = 0.5
    private const val EDGE_S = 10.0
    private const val MIN_SPAN_S = 30.0
    private const val MIN_TRIMMED_S = 120.0
    private const val GOOD_FIX_M = 30.0
    private const val M_PER_DEG = 111_320.0
    private const val EARTH_M = 6_371_000.0
    private const val BOX_LAT = 2.5
    private const val BOX_LNG = -27.5
    private const val ALT_BASE_M = 100.0
    private const val FULL_DEG = 360.0
    private const val MS = 1000.0
    private const val NS_PER_MS = 1_000_000L

    /** A private R0 export (`R0ExportTool`): times are trip milliseconds, [startWallMs] the wall clock at t = 0. */
    class Recording(
        val id: String,
        val platform: String,
        val startWallMs: Long,
        val fixes: List<LocationSample>,
        val motion: List<MotionSample>,
        val activity: List<ActivitySample>,
    ) {
        fun tS(monoNs: Long): Double = (monoNs - SyntheticCapture.START_MONO_NS) / NS_PER_S
        val startS: Double get() = tS(motion.first().tMonoNs)
        val endS: Double get() = tS(motion.last().tMonoNs)
        val wallStartS: Double get() = startWallMs / MS + startS
        val wallEndS: Double get() = startWallMs / MS + endS
    }

    data class Pairing(val candidate: Recording, val reference: Recording, val overlapS: Double)

    /** Candidate time = reference time + [shiftS]. */
    data class Sync(val shiftS: Double, val rho: Double)

    /** One labelled entry, or the reason the pair was not usable. */
    data class Entry(
        val id: String,
        val fixtureCsv: String?,
        val labelsCsv: String?,
        val summary: String,
    )

    fun parseExport(text: String): Recording {
        val meta = text.lineSequence().filter { it.startsWith("# ") && it.contains(": ") }
            .associate { it.removePrefix("# ").substringBefore(": ") to it.substringAfter(": ") }
        val fixes = mutableListOf<LocationSample>()
        val motion = mutableListOf<MotionSample>()
        val activity = mutableListOf<ActivitySample>()
        for (line in text.lineSequence()) {
            val c = line.split(',')
            when (c[0]) {
                "G" -> fixes += LocationSample(
                    mono(c[1]), wall(c[1]), c[2].toDouble(), c[3].toDouble(), c[4].d(), c[5].d(), c[6].d(), c[7].d(), c[8].d(),
                    c[9].d(), c[10].d(),
                )
                "M" -> motion += MotionSample(mono(c[1]), vec(c, 2), vec(c, 5), vec(c, 8), null)
                "A" -> activity += ActivitySample(mono(c[1]), wall(c[1]), ActivityType.valueOf(c[2]), ActivityConfidence.valueOf(c[3]))
                else -> Unit
            }
        }
        return Recording(
            meta.getValue("id"), meta["platform"] ?: "unknown", meta.getValue("start_wall_ms").toLong(),
            fixes.sortedBy { it.tMonoNs }, motion.sortedBy { it.tMonoNs }, activity.sortedBy { it.tMonoNs },
        )
    }

    /** Each candidate with the reference that overlaps it most in wall time, when that is at least 5 min. */
    fun pair(candidates: List<Recording>, references: List<Recording>): List<Pairing> = candidates.filter { it.motion.isNotEmpty() }
        .mapNotNull { c ->
            references.filter { it.motion.isNotEmpty() }
                .map { r -> Pairing(c, r, min(c.wallEndS, r.wallEndS) - max(c.wallStartS, r.wallStartS)) }
                .maxByOrNull { it.overlapS }?.takeIf { it.overlapS >= MIN_OVERLAP_S }
        }

    /**
     * Clock offset between the phones: wall clocks first, then the lag (±10 s) that best correlates each phone's
     * rotation rate about its own vertical, which neither the mount nor picking the phone up changes much.
     */
    fun sync(c: Recording, r: Recording): Sync {
        val coarse = (r.startWallMs - c.startWallMs) / MS
        val (c0, yc) = verticalYaw(c)
        val (r0, yr) = verticalYaw(r)
        var best = Sync(coarse, -1.0)
        val steps = (SYNC_SEARCH_S * A19Reference.FS).toInt()
        for (s in -steps..steps) {
            val shift = coarse + s / A19Reference.FS
            // Reference sample k sits at candidate time r0 + k/FS + shift, which is candidate sample k + offset.
            val offset = ((r0 + shift - c0) * A19Reference.FS).roundToInt()
            val rho = correlation(yr, yc, offset)
            if (rho > best.rho) best = Sync(shift, rho)
        }
        return best
    }

    private fun verticalYaw(rec: Recording): Pair<Double, DoubleArray> {
        val t = rec.motion.map { rec.tS(it.tMonoNs) }.toDoubleArray()
        val x = DoubleArray(rec.motion.size) { i ->
            val m = rec.motion[i]
            val u = m.gravityUp
            val norm = sqrt(u.x * u.x + u.y * u.y + u.z * u.z).takeIf { it > 0 } ?: 1.0
            ((m.rotRateDps.x * u.x + m.rotRateDps.y * u.y + m.rotRateDps.z * u.z) / norm).coerceIn(-YAW_CLIP_DPS, YAW_CLIP_DPS)
        }
        val n = ((t.last() - t.first()) * A19Reference.FS).toInt() + 1
        return t.first() to A19Reference.a3(A19Reference.resample(t, x, t.first(), n).first)
    }

    /** Pearson correlation of `a[k]` with `b[k + offset]` over the overlap. */
    private fun correlation(a: DoubleArray, b: DoubleArray, offset: Int): Double {
        val from = max(0, -offset)
        val to = min(a.size, b.size - offset)
        if (to - from < MIN_SYNC_SAMPLES) return -1.0
        var sa = 0.0
        var sb = 0.0
        for (k in from until to) { sa += a[k]; sb += b[k + offset] }
        val ma = sa / (to - from)
        val mb = sb / (to - from)
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (k in from until to) {
            val x = a[k] - ma
            val y = b[k + offset] - mb
            sab += x * y; saa += x * x; sbb += y * y
        }
        return if (saa == 0.0 || sbb == 0.0) -1.0 else sab / sqrt(saa * sbb)
    }

    /**
     * Builds the entry [outId] for [p]. [tripTimeBase] replays a written fixture as `LabelledSetTest` does and returns
     * the seconds from fixture `t = 0` to the replayed trip's `startedAt`, so labels land on the harness's time base.
     */
    @Suppress("ReturnCount") // one early return per reason a pair is skipped
    fun entry(p: Pairing, outId: String, trimM: Double = 1000.0, tripTimeBase: (String, String) -> Double): Entry {
        val c = p.candidate
        val r = p.reference
        val head = "$outId candidate=${c.id} reference=${r.id} overlap=${p.overlapS.roundToInt()}s"
        val sync = sync(c, r)
        if (sync.rho < MIN_SYNC_RHO) return Entry(outId, null, null, "$head SKIP clock sync rho=${sync.rho.f2()}")
        val frame = A19Reference.frame(r.motion, r.fixes, r::tS)
        if (!frame.usable) {
            return Entry(outId, null, null, "$head SKIP reference never held still with a solvable frame (best rho=${frame.gnssRho.f2()})")
        }
        val window = trimWindow(c, trimM) ?: return Entry(outId, null, null, "$head SKIP too short after the ${trimM.roundToInt()} m trim")
        val csv = fixture(c, outId, window, trimM)
        val base = tripTimeBase(outId, csv)
        fun toTrip(tRef: Double) = tRef + sync.shiftS - window.start - base
        val tripEnd = window.endInclusive - window.start - base
        val refSpan = (max(r.startS, frame.t0S) + EDGE_S)..(r.endS - EDGE_S)
        val covered = A19Reference.subtract(listOf(refSpan), frame.unstable)
            .map { max(0.0, toTrip(it.start))..min(tripEnd, toTrip(it.endInclusive)) }
            .filter { it.endInclusive - it.start >= MIN_SPAN_S }
        // No coverage is not "all covered" (an empty Coverage means the whole trip): with nothing to judge, skip the pair.
        if (covered.isEmpty()) {
            return Entry(outId, null, null, "$head SKIP no reference coverage ≥ ${MIN_SPAN_S.roundToInt()} s in the trimmed window")
        }
        val coverage = Coverage(covered)
        val refLabels = A19Reference.labels(frame).filter { l -> covered.any { s -> toTrip(l.tS) in s } }
        val labels = refLabels.map { LabelledEvent(it.type, toTrip(it.tS), it.uncertain) }
        val why = refLabels.mapNotNull { it.why }.groupingBy { it }.eachCount().toSortedMap()
        val perType = labels.groupBy { it.type }.toSortedMap().entries
            .joinToString(" ") { (t, l) -> "$t=${l.count { !it.uncertain }}+${l.count { it.uncertain }}?" }
        val summary = "$head shift=${sync.shiftS.f2()}s syncRho=${sync.rho.f2()} refRho=${frame.gnssRho.f2()} " +
            "unstableS=${seconds(frame.unstable)} coveredS=${seconds(covered)} " +
            "km=${(trackM(c.fixes.filter { c.tS(it.tMonoNs) in window }) / MS).f2()} $perType uncertainWhy=$why"
        return Entry(outId, csv, EventHarness.writeLabels(outId, labels, coverage), summary)
    }

    /** Trip seconds kept after removing [trimM] of track at each end (protocol step 3), or null if too little is left. */
    @Suppress("ReturnCount") // one early return per reason too little track is left
    fun trimWindow(c: Recording, trimM: Double): ClosedFloatingPointRange<Double>? {
        val good = c.fixes.filter { (it.hAccM ?: 0.0) <= GOOD_FIX_M }
        if (good.size < 2) return null
        val cum = DoubleArray(good.size)
        for (i in 1 until good.size) cum[i] = cum[i - 1] + haversineM(good[i - 1], good[i])
        val total = cum.last()
        val a = good.indices.firstOrNull { cum[it] >= trimM } ?: return null
        val b = good.indices.lastOrNull { total - cum[it] >= trimM } ?: return null
        val w = c.tS(good[a].tMonoNs)..c.tS(good[b].tMonoNs)
        return w.takeIf { it.endInclusive - it.start >= MIN_TRIMMED_S }
    }

    /**
     * The §8.2 fixture for the window: rebased to t = 0, the track rotated about its centroid by a random angle that is
     * never stored and moved into the ocean test box, course corrected by the same angle, altitude made relative, and
     * no wall clock, parking anchor, provider or note that could identify a place.
     */
    fun fixture(
        c: Recording,
        id: String,
        window: ClosedFloatingPointRange<Double>,
        trimM: Double,
        angleRad: Double = Math.random() * 2 * PI,
    ): String {
        val fixes = c.fixes.filter { c.tS(it.tMonoNs) in window }
        val lat0 = fixes.map { it.lat }.average()
        val lng0 = fixes.map { it.lng }.average()
        val alt0 = fixes.mapNotNull { it.altM }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        fun ms(monoNs: Long) = ((c.tS(monoNs) - window.start) * MS).roundToInt().toString()
        fun opt(x: Double?, d: Int) = x?.let { fixed(it, d) } ?: ""
        return buildString {
            appendLine(FixtureCsv.PRIVACY_HEADER)
            appendLine("# dpfx: 1")
            appendLine("# id: $id")
            appendLine("# source: recorded-trimmed")
            appendLine("# level: R0")
            appendLine("# platform: ${c.platform}")
            appendLine("# imu_hz: 50")
            appendLine("# trim_m: ${trimM.roundToInt()}")
            appendLine("# transform: translate+rotate")
            appendLine("# note: A19 labelled field drive; private, never commit")
            for (f in fixes) {
                val e = (f.lng - lng0) * cos(Math.toRadians(lat0)) * M_PER_DEG
                val n = (f.lat - lat0) * M_PER_DEG
                val e2 = e * cosA - n * sinA
                val n2 = e * sinA + n * cosA
                val lat = BOX_LAT + n2 / M_PER_DEG
                val lng = BOX_LNG + e2 / (M_PER_DEG * cos(Math.toRadians(BOX_LAT)))
                // Rotating the plane counter-clockwise by the angle turns compass bearings clockwise-negative by it.
                val course = f.courseDeg?.let { ((it - Math.toDegrees(angleRad)) % FULL_DEG + FULL_DEG) % FULL_DEG }
                appendLine(
                    listOf(
                        "G", ms(f.tMonoNs), fixed(lat, 7), fixed(lng, 7), opt(f.altM?.let { it - alt0 + ALT_BASE_M }, 1), opt(f.hAccM, 1),
                        opt(f.vAccM, 1), opt(f.speedMps, 2), opt(f.speedAccMps, 2), opt(course, 1), opt(f.courseAccDeg, 1),
                    ).joinToString(","),
                )
            }
            for (m in c.motion.filter { c.tS(it.tMonoNs) in window }) {
                val v = listOf(m.userAccelG, m.gravityUp).flatMap { listOf(fixed(it.x, 4), fixed(it.y, 4), fixed(it.z, 4)) }
                val g = m.rotRateDps
                val r = listOf(fixed(g.x, 3), fixed(g.y, 3), fixed(g.z, 3), "", "", "", "")
                appendLine((listOf("M", ms(m.tMonoNs)) + v + r).joinToString(","))
            }
            // The classification in force at the window start carries over to t = 0.
            val before = c.activity.lastOrNull { c.tS(it.tMonoNs) < window.start }
            before?.let { appendLine("A,0,${it.type.name},${it.confidence.name}") }
            for (a in c.activity.filter { c.tS(it.tMonoNs) in window }) appendLine("A,${ms(a.tMonoNs)},${a.type.name},${a.confidence.name}")
        }
    }

    private fun seconds(spans: List<ClosedFloatingPointRange<Double>>): Int = spans.sumOf { it.endInclusive - it.start }.roundToInt()

    private fun trackM(fixes: List<LocationSample>): Double = fixes.zipWithNext().sumOf { (a, b) -> haversineM(a, b) }

    fun haversineM(a: LocationSample, b: LocationSample): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_M * asin(sqrt(h))
    }

    private fun Double.f2(): String = "%.2f".format(this)

    private fun vec(c: List<String>, i: Int) = Vec3(c[i].toDouble(), c[i + 1].toDouble(), c[i + 2].toDouble())

    private fun mono(tMs: String): Long = SyntheticCapture.START_MONO_NS + tMs.toLong() * NS_PER_MS

    private fun wall(tMs: String): Long = SyntheticCapture.WALL0 + tMs.toLong()

    private fun String.d(): Double? = if (isEmpty()) null else toDouble()

    private const val NS_PER_S = 1e9
}
