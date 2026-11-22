package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.EventType
import com.driveperf.pipeline.score.Coverage
import com.driveperf.pipeline.score.EventHarness
import com.driveperf.pipeline.score.TypeCounts
import com.driveperf.testing.FixtureCsv
import com.driveperf.testing.SyntheticCapture
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DRIVE-93 AC2 on files: every `<id>.csv` in the labelled set with a `<id>.labels.csv` beside it is run through the
 * full pipeline and the detected events must meet the Q20 precision and recall targets per type, over the whole set.
 * Both files start with the `# privacy-trimmed` header (ADR-008), as every §8.2 fixture does.
 *
 * The set is the committed synthetic one (`src/jvmTest/resources/labelled`, which must equal its generator) unless
 * `-PlabelledSet=<dir>` names another, such as the A19 field set, which never enters the repository:
 * `./gradlew :shared:pipeline:jvmTest --tests '*LabelledSetTest' -PlabelledSet=/path/to/a19`.
 */
class LabelledSetTest {
    private val committed = File(System.getProperty("driveperf.labelledFixtures"))
    private val set = File(System.getProperty("driveperf.labelledSet"))
    private val regenerate = System.getProperty("driveperf.regenerateFixtures") == "true"

    @Test
    fun theCommittedSyntheticSetMatchesItsGenerator() {
        for (l in LabelledFixtures.all) {
            val csv = FixtureCsv.write(FixtureCsv.Meta(l.fixture.id, l.fixture.note), l.fixture.streams)
            val labels = EventHarness.writeLabels(l.fixture.id, l.labels)
            val csvFile = File(committed, "${l.fixture.id}.csv")
            val labelsFile = File(committed, "${l.fixture.id}.labels.csv")
            if (regenerate) {
                csvFile.writeText(csv)
                labelsFile.writeText(labels)
            }
            assertEquals(csv, csvFile.readText(), "${l.fixture.id}.csv differs from the generator; regenerate deliberately")
            assertEquals(labels, labelsFile.readText())
            assertTrue(csvFile.readLines().first() == FixtureCsv.PRIVACY_HEADER)
            assertTrue(labelsFile.readLines().first() == FixtureCsv.PRIVACY_HEADER, "${labelsFile.name}: ADR-008 header")
        }
    }

    @Test
    fun theLabelledSetMeetsThePrecisionAndRecallTargets() = runTest {
        val harness = PipelineHarness()
        val external = set.canonicalFile != committed.canonicalFile
        val labelled = set.listFiles { f -> f.name.endsWith(".labels.csv") }.orEmpty().sortedBy { it.name }
        assertTrue(labelled.isNotEmpty(), "no <id>.labels.csv in $set")
        var km = 0.0
        var totalKm = 0.0
        val perTrip = labelled.map { labelsFile ->
            val id = labelsFile.name.removeSuffix(".labels.csv")
            listOf(labelsFile, File(set, "$id.csv")).forEach { f ->
                assertTrue(f.readLines().first() == FixtureCsv.PRIVACY_HEADER, "${f.name}: no '# privacy-trimmed' first line (ADR-008)")
            }
            val parsed = FixtureCsv.parse(File(set, "$id.csv").readText())
            val trip = checkNotNull(harness.run(captureOf(id, parsed)).trip) { "$id finalised to no trip" }
            val text = labelsFile.readText()
            val coverage = EventHarness.parseCoverage(text)
            val counts = EventHarness.evaluate(EventHarness.of(trip.events), EventHarness.parseLabels(text), coverage = coverage)
            val tripKm = (trip.trip.distanceM ?: 0.0) / M_PER_KM
            // Rates are per covered km: the counts leave out everything outside the coverage, so the distance must too.
            val base = (trip.trip.startedAt - SyntheticCapture.WALL0) / MS_PER_S
            val coveredKm = if (coverage.spans.isEmpty()) tripKm else coveredM(parsed.fixes, coverage, base) / M_PER_KM
            km += coveredKm
            totalKm += tripKm
            println("DRIVE-93 trip $id ${"%.1f".format(tripKm)} km, ${"%.1f".format(coveredKm)} km covered $counts")
            counts
        }
        val counts = EventHarness.total(perTrip)
        val kms = "${"%.1f".format(totalKm)} km, ${"%.1f".format(km)} km covered"
        println("DRIVE-93 labelled set ${set.name}: ${labelled.size} trips, $kms, $counts")
        counts.forEach { (type, c) -> println("DRIVE-93 ${report(type, c, km)}") }
        // The committed set is labelled by construction and judged in full; a field set needs enough confirmed labels.
        val minLabels = if (external) EventHarness.MIN_LABELS else 0
        if (external) {
            EventHarness.insufficient(counts).forEach { println("DRIVE-93 $it: insufficient data (< $minLabels confirmed labels)") }
        }
        assertEquals(emptyList(), EventHarness.failures(counts, minLabels), counts.toString())
    }

    private fun report(type: EventType, c: TypeCounts, km: Double): String {
        fun rate(k: Int, n: Int): String = EventHarness.wilson95(k, n)?.let { ci ->
            "%.0f %% [%.0f–%.0f]".format(PCT * k / n, PCT * ci.start, PCT * ci.endInclusive)
        } ?: "unavailable"
        fun per100(x: Int): String = if (km <= 0.0) "unavailable" else "%.1f".format(x * HUNDRED_KM / km)
        return "$type TP=${c.truePositives} FP=${c.falsePositives} FN=${c.falseNegatives} " +
            "precision ${rate(c.truePositives, c.truePositives + c.falsePositives)} " +
            "recall ${rate(c.truePositives, c.truePositives + c.falseNegatives)} " +
            "per 100 km TP ${per100(c.truePositives)} FP ${per100(c.falsePositives)} FN ${per100(c.falseNegatives)}"
    }

    internal companion object {
        private const val M_PER_KM = 1000.0
        private const val MS_PER_S = 1000.0
        private const val NS_PER_S = 1e9
        private const val HUNDRED_KM = 100.0
        private const val PCT = 100.0

        /** Track metres between consecutive fixes that both fall inside [coverage], in trip seconds (fixture t − [baseS]). */
        fun coveredM(fixes: List<LocationSample>, coverage: Coverage, baseS: Double): Double = fixes.zipWithNext()
            .filter { (a, b) -> listOf(a, b).all { (it.tMonoNs - SyntheticCapture.START_MONO_NS) / NS_PER_S - baseS in coverage } }
            .sumOf { (a, b) -> A19Labeller.haversineM(a, b) }

        /** How a labelled fixture is replayed: a manual capture of its streams. The A19 tool uses it for the time base. */
        fun captureOf(id: String, p: FixtureCsv.Parsed) = CapturedTrip(
            CaptureHeader(id, SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0, -300, "Etc/GMT+5", WakeKind.MANUAL, null, 50, null),
            p.fixes, p.motion, p.activity, emptyList(), emptyList(),
        )
    }
}
