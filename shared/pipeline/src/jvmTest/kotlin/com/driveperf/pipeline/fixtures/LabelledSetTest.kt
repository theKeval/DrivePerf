package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.score.EventHarness
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
        val labelled = set.listFiles { f -> f.name.endsWith(".labels.csv") }.orEmpty().sortedBy { it.name }
        assertTrue(labelled.isNotEmpty(), "no <id>.labels.csv in $set")
        val perTrip = labelled.map { labelsFile ->
            val id = labelsFile.name.removeSuffix(".labels.csv")
            listOf(labelsFile, File(set, "$id.csv")).forEach { f ->
                assertTrue(f.readLines().first() == FixtureCsv.PRIVACY_HEADER, "${f.name}: no '# privacy-trimmed' first line (ADR-008)")
            }
            val parsed = FixtureCsv.parse(File(set, "$id.csv").readText())
            val trip = checkNotNull(harness.run(captureOf(id, parsed)).trip) { "$id finalised to no trip" }
            EventHarness.evaluate(EventHarness.of(trip.events), EventHarness.parseLabels(labelsFile.readText()))
        }
        val counts = EventHarness.total(perTrip)
        println("DRIVE-93 labelled set ${set.name}: ${labelled.size} trips, $counts")
        assertEquals(emptyList(), EventHarness.failures(counts), counts.toString())
    }

    private fun captureOf(id: String, p: FixtureCsv.Parsed) = CapturedTrip(
        CaptureHeader(id, SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0, -300, "Etc/GMT+5", WakeKind.MANUAL, null, 50, null),
        p.fixes, p.motion, p.activity, emptyList(), emptyList(),
    )
}
