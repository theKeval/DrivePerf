package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.Versions
import com.driveperf.testing.FixtureCsv
import com.driveperf.testing.FixtureCsv.fixed
import com.driveperf.testing.SyntheticCapture
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The committed fixture files (`shared/testing/fixtures/drives/<id>.csv` and `<id>.golden.json`) are exactly the
 * synthetic generator's output, carry the privacy header, stay under the §8.2 limits, and parse back into the same
 * pipeline result the goldens record. Run with `-PregenerateFixtures=true` to rewrite them after a deliberate change.
 */
class FixtureFilesTest {
    private val dir = File(System.getProperty("driveperf.fixtures"))
    private val regenerate = System.getProperty("driveperf.regenerateFixtures") == "true"
    private val harness = PipelineHarness()

    @Test
    fun fixtureFilesMatchTheGeneratorAndTheirGoldens() = runTest {
        for (f in DriveFixtures.all) {
            val csv = FixtureCsv.write(FixtureCsv.Meta(f.id, f.note), f.streams)
            val csvFile = File(dir, "${f.id}.csv")
            val goldenFile = File(dir, "${f.id}.golden.json")
            val parsed = FixtureCsv.parse(csv)
            val result = harness.trip(captureOf(f.id, parsed))
            val golden = golden(f.id, result)
            if (regenerate) {
                csvFile.writeText(csv)
                goldenFile.writeText(golden)
            }
            assertEquals(csv, csvFile.readText(), "${f.id}.csv differs from the generator; regenerate deliberately")
            assertEquals(golden, goldenFile.readText(), "${f.id}.golden.json differs from the pipeline output")
            assertTrue(csvFile.readLines().first() == FixtureCsv.PRIVACY_HEADER)
            assertTrue(csvFile.length() <= MAX_BYTES, "${f.id} is ${csvFile.length()} bytes (limit 2 MB)")
            assertTrue((parsed.motion.last().tMonoNs - parsed.motion.first().tMonoNs) / 1e9 <= MAX_SECONDS)
            assertEquals(f.streams.motion, parsed.motion, "parsing is exact")
            assertEquals(f.streams.fixes, parsed.fixes)
        }
    }

    private fun captureOf(id: String, p: FixtureCsv.Parsed) = CapturedTrip(
        CaptureHeader(id, SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0, -300, "Etc/GMT+5", WakeKind.MANUAL, null, 50, null),
        p.fixes, p.motion, p.activity, emptyList(), emptyList(),
    )

    /** The §8.2 golden shape, written deterministically. */
    private fun golden(id: String, r: FinalisedTrip): String {
        val q = r.trip.quality!!
        val s = r.score
        val ledger = s?.ledger?.let { l ->
            val t = l.perEvent.fold(l.base) { acc, row -> acc + row.row }
            val parts = listOf("\"T\": ${fixed(t.movingS, 1)}", "\"brake\": ${fixed(t.brake, 2)}")
            val more = listOf("\"accel\": ${fixed(t.accel, 2)}", "\"corner\": ${fixed(t.corner, 2)}")
            (parts + more).joinToString(", ", "{ ", " }")
        } ?: "null"
        val events = r.events.joinToString(",\n      ", "[\n      ", "\n    ]") {
            val head = "\"type\": \"${it.type}\", \"tPeakS\": ${fixed(it.tPeakS, 2)}"
            "{ $head, \"peakG\": ${fixed(it.peakG, 3)}, \"confidence\": \"${it.confidence}\" }"
        }.let { if (r.events.isEmpty()) "[]" else it }
        val spans = q.spans.joinToString(",\n      ", "[\n      ", "\n    ]") {
            """{ "kind": "${it.kind}", "t0S": ${fixed(it.t0S, 2)}, "t1S": ${fixed(it.t1S, 2)}, "reason": "${it.reason}" }"""
        }.let { if (q.spans.isEmpty()) "[]" else it }
        val calibrating = s?.calibrating?.let { """{ "k": ${it.k}, "of": ${it.of} }""" } ?: "null"
        return """
            |{
            |  "fixture": "$id",
            |  "pipelineVersion": "${Versions.PIPELINE}",
            |  "algorithmVersion": "${Versions.SCORING}",
            |  "priorTrips": [],
            |  "expect": {
            |    "badge": "${q.badge}",
            |    "context": "${r.trip.context}",
            |    "smoothness": ${s?.smoothness},
            |    "calibrating": $calibrating,
            |    "distanceM": ${fixed(r.trip.distanceM ?: 0.0, 1)},
            |    "ledger": $ledger,
            |    "events": $events,
            |    "spans": $spans,
            |    "tip": null
            |  },
            |  "tolerance": { "ledger": 0.02, "tPeakS": 0.04, "peakG": 0.002, "distanceM": 1.0 }
            |}
            |
        """.trimMargin()
    }

    private companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_SECONDS = 600.0
    }
}
