package com.driveperf.pipeline

import com.driveperf.pipeline.score.Goldens
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DRIVE-108 (F-SCO-07 AC4): a scoring version ships only when the guardrail and golden suites pass for it. CI runs this
 * with those suites as the "Scoring release gate" step of `shared.yml`. The fixture goldens record
 * [Versions.SCORING] themselves (`FixtureFilesTest`), and the §8.3 table records it here, so changing the version
 * without re-running and regenerating both fails the gate.
 */
class ScoringReleaseGateTest {
    @Test
    fun theGoldenTableWasRegeneratedForTheShippingScoringVersion() {
        assertEquals(
            Goldens.TABLE_VERSION,
            Versions.SCORING,
            "Versions.SCORING changed: re-run the §8.3 goldens and guardrails (G-06, G-07, G-11), regenerate the table " +
                "and the fixture goldens (-PregenerateFixtures=true), then set Goldens.TABLE_VERSION",
        )
    }

    @Test
    fun versionsAreWellFormed() {
        val id = Regex("""[a-z]+-\d+\.\d+\.\d+""")
        assertEquals(true, id.matches(Versions.SCORING), Versions.SCORING)
        assertEquals(true, id.matches(Versions.PIPELINE), Versions.PIPELINE)
    }
}
