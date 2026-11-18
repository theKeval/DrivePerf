package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.FinalisedTrip
import com.driveperf.pipeline.Versions
import com.driveperf.testing.FixtureCsv.fixed

/** The §8.2 golden file for a fixture's pipeline result. */
internal object FixtureGolden {
    /** The §8.2 golden shape, written deterministically. */
    fun write(id: String, r: FinalisedTrip): String {
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
        val tip = s?.tip?.let { """{ "tipId": "${it.tipId}", "category": "${it.category}" }""" } ?: "null"
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
            |    "tip": $tip
            |  },
            |  "tolerance": { "ledger": 0.02, "tPeakS": 0.04, "peakG": 0.002, "distanceM": 1.0 }
            |}
            |
        """.trimMargin()
    }
}
