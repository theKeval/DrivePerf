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
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.PipelineFinaliser
import com.driveperf.pipeline.capture.FinaliseRequest
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
    private val startWall = mutableMapOf<String, Long>()

    @Test
    fun diagnoseLocalDrives() = runTest(timeout = kotlin.time.Duration.parse("30m")) {
        val dir = System.getenv(ENV)?.takeIf { it.isNotBlank() } ?: return@runTest
        val only = System.getenv(ONLY_ENV)?.takeIf { it.isNotBlank() }
        val files = File(dir).listFiles { f -> f.name.endsWith(".csv") }.orEmpty().sortedBy { it.name }
        // Optional "trip id,ended_at epoch ms" lines from the trip table, so the replay ends where the app ended the trip.
        val ends = System.getenv(ENDS_ENV)?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).readLines().filter { it.contains(',') }
                .associate { it.substringBefore(',') to it.substringAfter(',').trim().toLong() }
        }.orEmpty()
        for (f in files.filter { only == null || it.name.startsWith(only) }) {
            val capture = variant(parse(f.readText()))
            val endNs = ends[capture.header.tripId]?.let { end ->
                startWall[capture.header.tripId]?.let { START + (end - it) * NS_PER_MS }
            }
            if (capture.fixes.size < MIN_FIXES) {
                println("DIAG ${capture.header.tripId} skipped: ${capture.fixes.size} fixes")
            } else {
                report(capture, endNs)
            }
        }
    }

    /** DRIVE-212/214: start distance (Q2) and the estimated-start outcome per trip. */
    private fun startReplay(capture: CapturedTrip, trip: com.driveperf.domain.trip.Trip?) {
        val anchor = capture.header.parking?.point
        val firstAccurate = capture.fixes.firstOrNull { it.hAccM?.let { a -> a <= 20.0 } == true }
        val q2 = if (anchor != null && firstAccurate != null) {
            com.driveperf.pipeline.geo.Geo.haversineM(anchor.lat, anchor.lng, firstAccurate.lat, firstAccurate.lng)
        } else null
        val backdateS = trip?.let { (capture.header.startWallMs - it.startedAt) / 1000.0 }
        println(
            "STARTREPLAY ${capture.header.tripId} Q2=${q2?.r()} estimated=${trip?.startEstimated} backdateS=$backdateS " +
                "method=${trip?.captureMeta?.startEstimateMethod} estimatedStartM=${trip?.quality?.estimatedStartM}",
        )
    }

    private fun variant(raw: CapturedTrip): CapturedTrip {
        val variant = System.getenv(VARIANT_ENV)?.takeIf { it.isNotBlank() } ?: return raw
        val motion = if (variant.startsWith(TRACKER)) {
            retrack(raw.motion, variant.removePrefix(TRACKER).toDouble())
        } else {
            resplit(raw.motion, variant.toDouble())
        }
        return raw.copy(motion = motion)
    }

    private suspend fun report(capture: CapturedTrip, endNs: Long?) {
        val id = capture.header.tripId
        val result = PipelineHarness().run(capture, manual = capture.header.wakeKind == WakeKind.MANUAL, endNs = endNs)
        val q = result.trips.firstOrNull()?.trip?.quality
        println("DIAG $id badge=${q?.badge} reason=${q?.reason} scoredS=${q?.scoredS?.r()} totalS=${q?.totalS?.r()}")
        println("DIAG $id placement=${q?.placement}")
        println("DIAG $id imu ${imuStats(capture.motion)}")
        val trip = result.trips.firstOrNull()?.trip
        startReplay(capture, trip)
        val bySpan = trip?.quality?.spans.orEmpty().groupBy { "${it.kind}/${it.reason}" }
            .mapValues { (_, v) -> v.sumOf { it.t1S - it.t0S }.toInt() }
        println("DIAG $id spansS $bySpan")
        val events = result.trips.firstOrNull()?.events.orEmpty()
        println(
            "DIAG $id events n=${events.size} " +
                events.joinToString(" ") { "${it.type}@${"%.2f".format(it.tPeakS)}:${it.peakG.r()}:${it.confidence}" },
        )
        if (q != null) whatIf(id, capture, endNs, q)

        val gate = GnssGate(cfg)
        val t0 = capture.header.startMonoNs
        val lastNs = endNs ?: maxOf(capture.fixes.last().tMonoNs, capture.motion.lastOrNull()?.tMonoNs ?: t0)
        val n = ((lastNs - t0) / NS * cfg.fs).toInt()
        val fixes = gate.gate(capture.fixes.filter { it.tMonoNs in t0..lastNs }, t0)
        val sig = DeviceSignal.resample(capture.motion.filter { it.tMonoNs in t0..lastNs }, t0, n, cfg)
        val movement = PhoneMovement(cfg)
        val spans = movement.spans(sig, fixes)
        val hitA = movement.offAxisHits(sig).size
        val hitB = movement.tiltHits(sig).size
        val hitC = movement.shockHits(sig, fixes).size
        val excludedS = spans.sumOf { it.count() } / cfg.fs.toDouble()
        val totalS = n / cfg.fs.toDouble()
        println("DIAG $id phone spans=${spans.size} excludedS=${excludedS.r()} of ${totalS.r()} hits offAxis=$hitA tilt=$hitB shock=$hitC")

        val alignment = Alignment(cfg)
        val epochs = epochs(n, spans)
        val lens = epochs.map { it.count() / cfg.fs }
        val longEnough = lens.count { it >= cfg.alignMinEpochS }
        println("DIAG $id epochs=${epochs.size} >=20s=$longEnough lengthsS=${lens.sortedDescending().take(LIST)}")
        val aligned = epochs.map { alignment.align(sig, it, fixes, 0.0) }
        val lag = GnssLag(cfg).estimateS(vehicleLon(sig, aligned), { sig.quality[it] and QualityBits.NOT_OK_MASK == 0 }, fixes)
        println("DIAG $id lagS=$lag")
        for (e in epochs.sortedByDescending { it.count() }.take(LIST)) {
            val d = alignment.diagnose(sig, e, fixes, lag)
            println("DIAG $id   epoch ${e.first / cfg.fs}s+${e.count() / cfg.fs}s ${d.fmt()}")
        }
        epochCauses(id, sig, epochs, fixes, lag, alignment)
        phoneSpanContext(id, spans, fixes)
        val whole = alignment.diagnose(sig, 0 until n, fixes, lag)
        println("DIAG $id   whole-drive solve (ignores phone spans) ${whole.fmt()}")
        val sweep = (LAG_FROM..LAG_TO).map { it * LAG_STEP }.map { it to alignment.diagnose(sig, 0 until n, fixes, it).coherence }
        println("DIAG $id   whole-drive rho by lag " + sweep.joinToString(" ") { (l, r) -> "${"%.2f".format(l)}:${"%.2f".format(r)}" })
        val best = sweep.maxBy { it.second }
        println("DIAG $id   best lag=${best.first} rho=${best.second.r()} ${alignment.diagnose(sig, 0 until n, fixes, best.first).fmt()}")
    }

    /**
     * The badge under alternative thresholds, as evidence for tuning reports only; the shipped [SignalConfig] is not
     * changed here. f is the §2.9 share: scored ÷ (total − estimated start); since DRIVE-164 stops are never excluded.
     */
    private suspend fun whatIf(id: String, capture: CapturedTrip, endNs: Long?, base: com.driveperf.domain.trip.TripQuality) {
        val estimated = base.spans.filter { it.kind == com.driveperf.domain.trip.SegmentKind.ESTIMATED }.sumOf { it.t1S - it.t0S }
        val share = { qq: com.driveperf.domain.trip.TripQuality ->
            val e = qq.spans.filter { it.kind == com.driveperf.domain.trip.SegmentKind.ESTIMATED }.sumOf { it.t1S - it.t0S }
            qq.scoredS / (qq.totalS - e)
        }
        println("DIAG $id share f=${share(base).r()} estimatedS=${estimated.r()} excludedS=${base.excludedS.r()}")
        val variants = listOf(
            "minKept10" to SignalConfig(alignMinKept = 10),
            "tilt30" to SignalConfig(tiltDeg = 30.0),
            "minKept10+tilt30" to SignalConfig(alignMinKept = 10, tiltDeg = 30.0),
        )
        val out = variants.map { (name, c) ->
            val trip = PipelineFinaliser(EMPTY_HISTORY, null, c)
                .finalise(FinaliseRequest(capture, endNs, TripKind.DRIVER, capture.header.wakeKind == WakeKind.MANUAL, null, 1L)).trip
            val qq = trip?.trip?.quality
            "$name=${qq?.badge}(f=${qq?.let { share(it).r() }})"
        }
        println("DIAG $id whatIf base=${base.badge}(f=${share(base).r()}) ${out.joinToString(" ")}")
    }

    /**
     * Seconds of non-converged epochs by first failing step-6 test, and how many of them a carried-over rotation could
     * cover: the previous converged epoch's up axis is within 10° of this epoch's (the phone settled back the same way).
     */
    @Suppress("LongParameterList")
    private fun epochCauses(id: String, sig: DeviceSignal, epochs: List<IntRange>, fixes: List<GatedFix>, lag: Double, al: Alignment) {
        val causes = mutableMapOf<String, Double>()
        var carry = 0.0
        var lastUp: V3? = null
        for (e in epochs) {
            val a = al.align(sig, e, fixes, lag)
            val d = al.diagnose(sig, e, fixes, lag)
            val s = e.count() / cfg.fs.toDouble()
            if (a.converged) {
                lastUp = a.eU
                continue
            }
            val cause = when {
                e.count() < cfg.alignMinEpochS * cfg.fs -> "shorterThan20s"
                d.kept < cfg.alignMinKept -> "fewerThan20Kept"
                d.coherence < cfg.alignMinCoherence -> "rhoBelow0.6"
                else -> "lowExcitation"
            }
            causes[cause] = (causes[cause] ?: 0.0) + s
            val prev = lastUp
            if (prev != null && kotlin.math.acos((prev dot a.eU).coerceIn(-1.0, 1.0)) * RAD_TO_DEG <= CARRY_DEG) carry += s
        }
        println("DIAG $id notAlignedS by cause ${causes.mapValues { it.value.toInt() }} carryOverCoversS=${carry.toInt()}")
    }

    /** Phone-moved spans while the car moves (median GNSS speed ≥ 2 m/s inside the span) or stands. */
    private fun phoneSpanContext(id: String, spans: List<IntRange>, fixes: List<GatedFix>) {
        var moving = 0
        var movingS = 0.0
        for (r in spans) {
            val t0 = r.first / cfg.fs.toDouble()
            val t1 = (r.last + 1) / cfg.fs.toDouble()
            val v = fixes.filter { it.tS in t0..t1 }.map { it.speedMps }.sorted()
            if (v.isNotEmpty() && v[v.size / 2] >= MOVING_MPS) {
                moving++
                movingS += t1 - t0
            }
        }
        println("DIAG $id phone spans whileMoving=$moving (${movingS.toInt()} s) whileStopped=${spans.size - moving}")
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
        meta["start_wall_ms"]?.let { startWall[meta.getValue("id")] = it.toLong() }
        val header = CaptureHeader(
            meta.getValue("id"), START, WALL0, 0, "UTC", WakeKind.valueOf(meta["wake_kind"] ?: "MANUAL"), meta["source_detail"],
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
        val EMPTY_HISTORY = object : com.driveperf.domain.repository.TripHistory {
            override suspend fun finishedBefore(beforeStartedAt: Long, limit: Int) = emptyList<com.driveperf.domain.repository.ScoredTrip>()
        }
        const val TRACKER = "tracker:"
        const val ENDS_ENV = "DRIVEPERF_REAL_DRIVES_ENDS"
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
        const val RAD_TO_DEG = 180.0 / kotlin.math.PI
        const val CARRY_DEG = 10.0
        const val MOVING_MPS = 2.0
        const val LAG_FROM = -16
        const val LAG_TO = 8
        const val LAG_STEP = 0.25
    }
}
