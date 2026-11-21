package com.driveperf.pipeline.fixtures

import com.driveperf.testing.FixtureCsv
import com.driveperf.testing.SyntheticCapture
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Developer tool, not a test (DRIVE-93 AC2, A19): builds the private labelled field set from paired drives. It does
 * nothing unless `DRIVEPERF_A19_IN` is set, so CI never sees private data.
 *
 * `DRIVEPERF_A19_IN` holds `R0ExportTool` CSVs in `ios/` and `android/`. The candidate platform (`DRIVEPERF_A19_CANDIDATE`,
 * default `ios`) is the phone under test; the other is the reference, fixed in the car. Each candidate drive with a
 * reference overlapping it by ≥ 5 min becomes `rec-a19-NN.csv` (privacy-trimmed §8.2 fixture) and
 * `rec-a19-NN.labels.csv` in `DRIVEPERF_A19_OUT`, which must be **outside the repository**. Existing entries are kept,
 * so labels stay frozen: `a19-frozen.txt` records each labels file's SHA-256. `a19-run.txt` maps entries to trip IDs.
 *
 * ```
 * DRIVEPERF_A19_IN=~/DrivePerf-a19/csv DRIVEPERF_A19_OUT=~/DrivePerf-a19/set \
 *   ./gradlew :shared:pipeline:jvmTest --rerun --tests '*A19LabelTool*'
 * ./gradlew :shared:pipeline:jvmTest --rerun --tests '*LabelledSetTest' -PlabelledSet=$HOME/DrivePerf-a19/set
 * ```
 */
class A19LabelTool {
    @Test
    fun buildA19Set() {
        val input = System.getenv(IN_ENV)?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val out = File(requireNotNull(System.getenv(OUT_ENV)) { "$OUT_ENV must be set with $IN_ENV" }).canonicalFile
        val repo = File(System.getProperty("user.dir")).canonicalFile.parentFile.parentFile
        require(!out.path.startsWith(repo.path)) { "$OUT_ENV must be outside the repository: real drives never enter it (ADR-008)" }
        val candidate = System.getenv(CANDIDATE_ENV) ?: "ios"
        val reference = if (candidate == "ios") "android" else "ios"
        fun read(platform: String) = File(input, platform).listFiles { f -> f.name.endsWith(".csv") }.orEmpty()
            .map { A19Labeller.parseExport(it.readText()) }
        val pairs = A19Labeller.pair(read(candidate), read(reference)).sortedBy { it.candidate.wallStartS }
        out.mkdirs()
        val log = StringBuilder()
        pairs.forEachIndexed { i, p ->
            val id = "rec-a19-%02d".format(i + 1)
            val csv = File(out, "$id.csv")
            val labels = File(out, "$id.labels.csv")
            val line = if (labels.exists() && csv.exists()) {
                "$id candidate=${p.candidate.id} reference=${p.reference.id} KEPT (frozen)"
            } else {
                val e = A19Labeller.entry(p, id) { fid, text -> tripTimeBase(fid, text) }
                if (e.fixtureCsv != null && e.labelsCsv != null) {
                    csv.writeText(e.fixtureCsv)
                    labels.writeText(e.labelsCsv)
                }
                e.summary
            }
            println("A19 $line")
            log.appendLine(line)
        }
        File(out, "a19-run.txt").appendText(log.toString())
        File(out, "a19-frozen.txt").writeText(
            out.listFiles { f -> f.name.endsWith(".labels.csv") }.orEmpty().sortedBy { it.name }
                .joinToString("") { "${sha256(it.readBytes())}  ${it.name}\n" },
        )
    }

    /** Seconds from fixture t = 0 to the replayed trip's start, replayed exactly as `LabelledSetTest` replays it. */
    private fun tripTimeBase(id: String, csv: String): Double = runBlocking {
        val result = PipelineHarness().run(LabelledSetTest.captureOf(id, FixtureCsv.parse(csv)))
        val trip = checkNotNull(result.trip) { "$id finalised to no trip" }
        (trip.trip.startedAt - SyntheticCapture.WALL0) / MS
    }

    private fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private companion object {
        const val IN_ENV = "DRIVEPERF_A19_IN"
        const val OUT_ENV = "DRIVEPERF_A19_OUT"
        const val CANDIDATE_ENV = "DRIVEPERF_A19_CANDIDATE"
        const val MS = 1000.0
    }
}
