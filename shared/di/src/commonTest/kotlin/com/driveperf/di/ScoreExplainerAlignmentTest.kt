package com.driveperf.di

import com.driveperf.pipeline.Versions
import com.driveperf.pipeline.score.ScoringConfig
import com.driveperf.presentation.score.ScoreExplainer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S-SCO-02 states the scoring version and the event thresholds (F-SCO-08 AC2). Presentation cannot see the pipeline,
 * so it mirrors them; this fails when the pipeline changes them without the explainer.
 */
class ScoreExplainerAlignmentTest {
    @Test
    fun theExplainerShowsTheShippedScoringVersionAndThresholds() {
        val config = ScoringConfig()
        assertEquals(Versions.SCORING, ScoreExplainer.CURRENT.id)
        assertEquals(config.brakeEnterG, ScoreExplainer.BRAKE_G)
        assertEquals(config.accelEnterG, ScoreExplainer.ACCEL_G)
        assertEquals(config.cornerEnterG, ScoreExplainer.CORNER_G)
    }
}
