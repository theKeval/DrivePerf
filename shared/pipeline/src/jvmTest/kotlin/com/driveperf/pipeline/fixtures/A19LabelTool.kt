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
 * so labels stay frozen: `a19-frozen.txt` records each labels file's SHA-256. `a19-map.txt` binds each entry ID to its
 * candidate and reference trip IDs, and `a19-run.txt` logs every run.
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
        val pairs = A19Labeller.pair(read(candidate), read(reference))
        build(pairs, out) { p, id -> A19Labeller.entry(p, id) { fid, text -> tripTimeBase(fid, text) } }
    }

    /** Seconds from fixture t = 0 to the replayed trip's start, replayed exactly as `LabelledSetTest` replays it. */
    private fun tripTimeBase(id: String, csv: String): Double = runBlocking {
        val result = PipelineHarness().run(LabelledSetTest.captureOf(id, FixtureCsv.parse(csv)))
        val trip = checkNotNull(result.trip) { "$id finalised to no trip" }
        (trip.trip.startedAt - SyntheticCapture.WALL0) / MS
    }

    internal companion object {
        /**
         * Writes the entries for [pairs] into [out]. An entry's ID is bound to its candidate and reference recordings in
         * `a19-map.txt`, never to its position, so a pair that becomes pairable later takes the next free ID and every
         * written entry keeps its files. Mapped entries with files are kept (frozen); mapped ones without files (skipped
         * before) are tried again under the same ID. Returns the summary lines.
         */
        fun build(
            pairs: List<A19Labeller.Pairing>,
            out: File,
            makeEntry: (A19Labeller.Pairing, String) -> A19Labeller.Entry,
        ): List<String> {
            out.mkdirs()
            val mapFile = File(out, MAP_FILE)
            val written = out.listFiles { f -> f.name.endsWith(".labels.csv") }.orEmpty()
            check(mapFile.exists() || written.isEmpty()) { "$out has entries but no $MAP_FILE: regenerate it into an empty folder" }
            val map = mapFile.takeIf { it.exists() }?.readLines().orEmpty().filter { it.isNotBlank() }
                .associate { line -> line.split(' ').let { (id, c, r) -> (c to r) to id } }.toMutableMap()
            var next = map.values.maxOfOrNull { it.removePrefix(ID_PREFIX).toInt() } ?: 0
            val log = pairs.sortedBy { it.candidate.wallStartS }.map { p ->
                val id = map.getOrPut(p.candidate.id to p.reference.id) { ID_PREFIX + "%02d".format(++next) }
                val csv = File(out, "$id.csv")
                val labels = File(out, "$id.labels.csv")
                if (labels.exists() && csv.exists()) {
                    "$id candidate=${p.candidate.id} reference=${p.reference.id} KEPT (frozen)"
                } else {
                    val e = makeEntry(p, id)
                    if (e.fixtureCsv != null && e.labelsCsv != null) {
                        csv.writeText(e.fixtureCsv)
                        labels.writeText(e.labelsCsv)
                    }
                    e.summary
                }
            }
            mapFile.writeText(map.entries.sortedBy { it.value }.joinToString("") { (k, id) -> "$id ${k.first} ${k.second}\n" })
            log.forEach { println("A19 $it") }
            File(out, "a19-run.txt").appendText(log.joinToString("") { "$it\n" })
            File(out, "a19-frozen.txt").writeText(
                out.listFiles { f -> f.name.endsWith(".labels.csv") }.orEmpty().sortedBy { it.name }
                    .joinToString("") { "${sha256(it.readBytes())}  ${it.name}\n" },
            )
            return log
        }

        private fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

        private const val MAP_FILE = "a19-map.txt"
        private const val ID_PREFIX = "rec-a19-"
        private const val IN_ENV = "DRIVEPERF_A19_IN"
        private const val OUT_ENV = "DRIVEPERF_A19_OUT"
        private const val CANDIDATE_ENV = "DRIVEPERF_A19_CANDIDATE"
        private const val MS = 1000.0
    }
}
