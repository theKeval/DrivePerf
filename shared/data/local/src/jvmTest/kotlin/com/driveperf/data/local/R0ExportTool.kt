package com.driveperf.data.local

import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.testing.FixtureCsv.fixed
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test

/**
 * Developer tool, not a test (DRIVE-160): decodes real R0 capture logs into the §8.2 fixture CSV layout so the
 * pipeline's `RealDriveDiagnostics` (which cannot depend on this module) can replay them.
 *
 * It does nothing unless `DRIVEPERF_R0_IN` is set, so CI never sees private data. Set it to an R0 file or a folder
 * searched for `r0*` files, and `DRIVEPERF_R0_OUT` to a folder **outside the repository**: the output keeps real
 * coordinates and is not privacy-trimmed. `DRIVEPERF_R0_PLATFORM` (`android` | `ios`) is copied into the metadata.
 *
 * ```
 * DRIVEPERF_R0_IN=/path/to/trips DRIVEPERF_R0_OUT=/tmp/r0csv ./gradlew :shared:data:local:jvmTest --tests '*R0ExportTool*'
 * ```
 */
class R0ExportTool {
    @Test
    fun exportLocalR0Files() {
        val input = System.getenv(IN_ENV)?.takeIf { it.isNotBlank() } ?: return
        val out = File(requireNotNull(System.getenv(OUT_ENV)) { "$OUT_ENV must be set with $IN_ENV" })
        val platform = System.getenv(PLATFORM_ENV) ?: "unknown"
        out.mkdirs()
        val files = File(input).let { root ->
            if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile && it.name.startsWith("r0") }.toList()
        }
        for (f in files.sortedBy { it.path }) {
            val bytes = f.readBytes().let { if (f.name.endsWith(".gz")) GZIPInputStream(it.inputStream()).readBytes() else it }
            val trip = TripFileCodec.decode(bytes)
            if (trip == null) {
                println("R0EXPORT skip ${f.path}: not a readable R0 file")
                continue
            }
            val target = File(out, "${trip.header.tripId}.csv")
            target.writeText(csv(trip, platform))
            println("R0EXPORT ${trip.header.tripId} fixes=${trip.fixes.size} motion=${trip.motion.size} marks=${trip.marks.size}")
        }
    }

    private fun csv(trip: CapturedTrip, platform: String): String = buildString {
        val h = trip.header
        fun ms(tMonoNs: Long): String = ((tMonoNs - h.startMonoNs) / NS_PER_MS).toString()
        fun opt(x: Double?, d: Int): String = x?.let { fixed(it, d) } ?: ""
        appendLine("# private: real R0 export, never commit")
        appendLine("# id: ${h.tripId}")
        appendLine("# platform: $platform")
        appendLine("# imu_hz: ${h.nominalImuHz}")
        appendLine("# wake_kind: ${h.wakeKind.name}")
        appendLine("# start_wall_ms: ${h.startWallMs}")
        h.parking?.let { appendLine("# parking: ${fixed(it.point.lat, 7)} ${fixed(it.point.lng, 7)} ${it.wallMs - h.startWallMs}") }
        for (f in trip.fixes) {
            appendLine(
                listOf(
                    "G", ms(f.tMonoNs), fixed(f.lat, 7), fixed(f.lng, 7), opt(f.altM, 1), opt(f.hAccM, 1), opt(f.vAccM, 1),
                    opt(f.speedMps, 2), opt(f.speedAccMps, 2), opt(f.courseDeg, 1), opt(f.courseAccDeg, 1),
                    f.provider?.name.orEmpty(), f.ageMs?.toString().orEmpty(),
                ).joinToString(","),
            )
        }
        for (m in trip.motion) {
            val v = listOf(m.userAccelG, m.gravityUp)
                .flatMap { listOf(fixed(it.x, 3), fixed(it.y, 3), fixed(it.z, 3)) }
            val r = listOf(fixed(m.rotRateDps.x, 2), fixed(m.rotRateDps.y, 2), fixed(m.rotRateDps.z, 2))
            appendLine((listOf("M", ms(m.tMonoNs)) + v + r + listOf("", "", "", "")).joinToString(","))
        }
        for (a in trip.activity) appendLine("A,${ms(a.tMonoNs)},${a.type.name},${a.confidence.name}")
        for (x in trip.marks) appendLine("X,${ms(x.tMonoNs)},${x.type.name},${x.detail.orEmpty()}")
    }

    private companion object {
        const val IN_ENV = "DRIVEPERF_R0_IN"
        const val OUT_ENV = "DRIVEPERF_R0_OUT"
        const val PLATFORM_ENV = "DRIVEPERF_R0_PLATFORM"
        const val NS_PER_MS = 1_000_000L
    }
}
