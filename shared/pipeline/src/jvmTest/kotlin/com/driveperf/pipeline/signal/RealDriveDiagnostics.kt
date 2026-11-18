package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.GravityTracker
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.fixtures.PipelineHarness
import com.driveperf.pipeline.score.QualityBits
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test

/**
 * Developer tool, not a test (DRIVE-160): replays real drives through R0 → R1 and logs why alignment does or does not
 * converge. It does nothing unless `DRIVEPERF_REAL_DRIVES` names a folder of CSVs written by the
 * `:shared:data:local` `R0ExportTool`, so CI never runs it on private data. Output goes to stdout (`-i`).
 *
 * ```
 * DRIVEPERF_REAL_DRIVES=/tmp/r0csv/android ./gradlew :shared:pipeline:jvmTest --tests '*RealDriveDiagnostics*' -i
 * ```
 */
class RealDriveDiagnostics {
    private val cfg = SignalConfig()

    @Test
    fun diagnoseLocalDrives() = runTest(timeout = kotlin.time.Duration.parse("30m")) {
        val dir = System.getenv(ENV)?.takeIf { it.isNotBlank() } ?: return@runTest
        val only = System.getenv(ONLY_ENV)?.takeIf { it.isNotBlank() }
        val files = File(dir).listFiles { f -> f.name.endsWith(".csv") }.orEmpty().sortedBy { it.name }
        for (f in files) {
            if (only != null && !f.name.startsWith(only)) continue
            val raw = parse(f.readText())
            val variant = System.getenv(VARIANT_ENV)?.takeIf { it.isNotBlank() }
            val capture = when {
                variant == null -> raw
                variant.startsWith("tracker:") -> raw.copy(motion = retrack(raw.motion, variant.removePrefix("tracker:").toDouble()))
                else -> raw.copy(motion = resplit(raw.motion, variant.toDouble()))
            }
            if (capture.fixes.size < MIN_FIXES) {
                println("DIAG ${capture.header.tripId} skipped: ${capture.fixes.size} fixes")
                continue
            }
            report(capture)
        }
    }

    private suspend fun report(capture: CapturedTrip) {
        val id = capture.header.tripId
        val result = PipelineHarness().run(capture, manual = capture.header.wakeKind == WakeKind.MANUAL)
        val q = result.trips.firstOrNull()?.trip?.quality
        println("DIAG $id badge=${q?.badge} reason=${q?.reason} scoredS=${q?.scoredS?.r()} totalS=${q?.totalS?.r()} placement=${q?.placement}")
        println("DIAG $id imu ${imuStats(capture.motion)}")

        val gate = GnssGate(cfg)
        val t0 = capture.header.startMonoNs
        val lastNs = maxOf(capture.fixes.last().tMonoNs, capture.motion.lastOrNull()?.tMonoNs ?: t0)
        val n = ((lastNs - t0) / NS * cfg.fs).toInt()
        val fixes = gate.gate(capture.fixes.filter { it.tMonoNs in t0..lastNs }, t0)
        val sig = DeviceSignal.resample(capture.motion.filter { it.tMonoNs in t0..lastNs }, t0, n, cfg)
        val movement = PhoneMovement(cfg)
        val spans = movement.spans(sig, fixes)
        val hitA = movement.offAxisHits(sig).size
        val hitB = movement.tiltHits(sig).size
        val hitC = movement.shockHits(sig, fixes).size
        val excludedS = spans.sumOf { it.count() } / cfg.fs.toDouble()
        println("DIAG $id phone spans=${spans.size} excludedS=${excludedS.r()} of ${(n / cfg.fs.toDouble()).r()} hits offAxis=$hitA tilt=$hitB shock=$hitC")

        val alignment = Alignment(cfg)
        val epochs = epochs(n, spans)
        val lens = epochs.map { it.count() / cfg.fs }
        println("DIAG $id epochs=${epochs.size} >=20s=${lens.count { it >= cfg.alignMinEpochS }} lengthsS=${lens.sortedDescending().take(LIST)}")
        val aligned = epochs.map { alignment.align(sig, it, fixes, 0.0) }
        val lag = GnssLag(cfg).estimateS(vehicleLon(sig, aligned), { sig.quality[it] and QualityBits.NOT_OK_MASK == 0 }, fixes)
        println("DIAG $id lagS=$lag")
        for (e in epochs.sortedByDescending { it.count() }.take(LIST)) {
            val d = alignment.diagnose(sig, e, fixes, lag)
            println("DIAG $id   epoch ${e.first / cfg.fs}s+${e.count() / cfg.fs}s ${d.fmt()}")
        }
        val whole = alignment.diagnose(sig, 0 until n, fixes, lag)
        println("DIAG $id   whole-drive solve (ignores phone spans) ${whole.fmt()}")
        val sweep = (LAG_FROM..LAG_TO).map { it * LAG_STEP }.map { it to alignment.diagnose(sig, 0 until n, fixes, it).coherence }
        println("DIAG $id   whole-drive rho by lag " + sweep.joinToString(" ") { (l, r) -> "${"%.2f".format(l)}:${"%.2f".format(r)}" })
        val best = sweep.maxBy { it.second }
        println("DIAG $id   best lag=${best.first} rho=${best.second.r()} ${alignment.diagnose(sig, 0 until n, fixes, best.first).fmt()}")
    }

    /**
     * Experiment: rebuild the specific force (`userAccel + gravityUp`, g) and split gravity out again with a centred
     * moving average of [windowS] seconds, to test whether the platform gravity absorbs sustained car acceleration.
     */
    private fun resplit(m: List<MotionSample>, windowS: Double): List<MotionSample> {
        val f = m.map { V3(it.userAccelG.x + it.gravityUp.x, it.userAccelG.y + it.gravityUp.y, it.userAccelG.z + it.gravityUp.z) }
        val prefix = Array(f.size + 1) { V3.ZERO }
        for (i in f.indices) prefix[i + 1] = prefix[i] + f[i]
        val halfNs = (windowS / 2 * NS).toLong()
        var lo = 0
        var hi = 0
        return m.mapIndexed { i, s ->
            while (m[lo].tMonoNs < s.tMonoNs - halfNs) lo++
            while (hi < m.size && m[hi].tMonoNs <= s.tMonoNs + halfNs) hi++
            val g = (prefix[hi] - prefix[lo]) * (1.0 / (hi - lo))
            val a = f[i] - g
            s.copy(userAccelG = Vec3(a.x, a.y, a.z), gravityUp = g.unit().let { Vec3(it.x, it.y, it.z) })
        }
    }

    /** Replays the recorded specific force and gyro through the Android adapter's [GravityTracker]. */
    private fun retrack(m: List<MotionSample>, tauS: Double): List<MotionSample> {
        val tracker = GravityTracker(tauS)
        return m.map { s ->
            val f = Vec3(s.userAccelG.x + s.gravityUp.x, s.userAccelG.y + s.gravityUp.y, s.userAccelG.z + s.gravityUp.z)
            val split = tracker.update(s.tMonoNs, f, s.rotRateDps)
            s.copy(userAccelG = split.userAccelG, gravityUp = split.gravityUp)
        }
    }

    private fun AlignmentDiagnostics.fmt(): String =
        "pairs=$pairs kept=$kept rho=${coherence.r()} excitationRmsG=${excitationRmsG.r()} phiDeg=${phiDeg.r()}"

    private fun vehicleLon(sig: DeviceSignal, aligned: List<EpochAlignment>): DoubleArray {
        val lon = DoubleArray(sig.size)
        for (a in aligned) for (k in a.epoch) lon[k] = (sig.accel[k] dot a.eF) - a.biasLon
        return lon
    }

    private fun epochs(n: Int, phone: List<IntRange>): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var from = 0
        for (span in phone.sortedBy { it.first }) {
            if (span.first > from) out += from until span.first
            from = maxOf(from, span.last + 1)
        }
        if (from < n) out += from until n
        return out
    }

    /** Sample spacing and how often gravity and gyro change between consecutive samples (stale streams repeat). */
    private fun imuStats(m: List<MotionSample>): String {
        if (m.size < 2) return "samples=${m.size}"
        val dts = m.zipWithNext { a, b -> (b.tMonoNs - a.tMonoNs) / 1e6 }.sorted()
        val gravityChanges = m.zipWithNext { a, b -> a.gravityUp != b.gravityUp }.count { it }
        val gyroChanges = m.zipWithNext { a, b -> a.rotRateDps != b.rotRateDps }.count { it }
        val accelChanges = m.zipWithNext { a, b -> a.userAccelG != b.userAccelG }.count { it }
        val gx = m.map { it.userAccelG }
        val mean = Vec3(gx.sumOf { it.x } / gx.size, gx.sumOf { it.y } / gx.size, gx.sumOf { it.z } / gx.size)
        val pairs = m.size - 1.0
        return "samples=${m.size} dtMs p50=${dts[dts.size / 2].r()} p99=${dts[dts.size * P99 / PCT].r()} " +
            "changed accel=${(accelChanges / pairs).r()} gravity=${(gravityChanges / pairs).r()} gyro=${(gyroChanges / pairs).r()} " +
            "meanAccelG=(${mean.x.r()}, ${mean.y.r()}, ${mean.z.r()})"
    }

    private fun parse(text: String): CapturedTrip {
        val meta = text.lineSequence().filter { it.startsWith("# ") && it.contains(": ") }
            .associate { it.removePrefix("# ").substringBefore(": ") to it.substringAfter(": ") }
        val fixes = mutableListOf<LocationSample>()
        val motion = mutableListOf<MotionSample>()
        val activity = mutableListOf<ActivitySample>()
        val marks = mutableListOf<CaptureMark>()
        for (line in text.lineSequence()) {
            val c = line.split(',')
            when (c[0]) {
                "G" -> fixes += LocationSample(
                    mono(c[1]), wall(c[1]), c[2].toDouble(), c[3].toDouble(), c[4].d(), c[5].d(), c[6].d(), c[7].d(), c[8].d(),
                    c[9].d(), c[10].d(),
                )
                "M" -> motion += MotionSample(mono(c[1]), vec(c, 2), vec(c, 5), vec(c, 8), null)
                "A" -> activity += ActivitySample(mono(c[1]), wall(c[1]), ActivityType.valueOf(c[2]), ActivityConfidence.valueOf(c[3]))
                "X" -> marks += CaptureMark(mono(c[1]), CaptureMarkType.valueOf(c[2]), c.getOrNull(3)?.ifEmpty { null })
                else -> Unit
            }
        }
        val parking = meta["parking"]?.split(' ')?.let { p -> Parking(GeoPoint(p[0].toDouble(), p[1].toDouble()), WALL0 + p[2].toLong()) }
        val header = CaptureHeader(
            meta.getValue("id"), START, WALL0, 0, "UTC", WakeKind.valueOf(meta["wake_kind"] ?: "MANUAL"), null,
            meta["imu_hz"]?.toInt() ?: NOMINAL_HZ, parking,
        )
        return CapturedTrip(header, fixes, motion, activity, emptyList(), marks)
    }

    private fun vec(c: List<String>, i: Int) = Vec3(c[i].toDouble(), c[i + 1].toDouble(), c[i + 2].toDouble())

    private fun mono(tMs: String): Long = START + tMs.toLong() * NS_PER_MS

    private fun wall(tMs: String): Long = WALL0 + tMs.toLong()

    private fun String.d(): Double? = if (isEmpty()) null else toDouble()

    private fun Double.r(): String = "%.3f".format(this)

    private companion object {
        const val ENV = "DRIVEPERF_REAL_DRIVES"
        const val ONLY_ENV = "DRIVEPERF_REAL_DRIVES_ONLY"
        const val VARIANT_ENV = "DRIVEPERF_REAL_DRIVES_RESPLIT_S"
        const val START = 1_000_000_000_000L
        const val WALL0 = 1_800_000_000_000L
        const val NS = 1e9
        const val NS_PER_MS = 1_000_000L
        const val NOMINAL_HZ = 50
        const val MIN_FIXES = 30
        const val LIST = 6
        const val P99 = 99
        const val PCT = 100
        const val LAG_FROM = -16
        const val LAG_TO = 8
        const val LAG_STEP = 0.25
    }
}
