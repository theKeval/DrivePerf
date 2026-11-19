package com.driveperf.presentation.score

import com.driveperf.domain.trip.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreExplainerTest {
    private val content = ScoreExplainer.content { "10 November 2026" }

    @Test
    fun itExplainsEverythingTheStoryAsksForInOrder() {
        assertEquals(
            listOf(
                "Your usual", "What moves it", "Events", "Was it needed?", "Fair by design", "Data quality",
                "Where numbers come from", "Efficiency habits", "What it isn’t",
            ),
            content.sections.map { it.title },
        )
        assertEquals(
            listOf(ExplainerSectionKind.EVENTS, ExplainerSectionKind.QUALITY, ExplainerSectionKind.PROVENANCE),
            content.sections.map { it.kind }.filter { it != ExplainerSectionKind.TEXT },
        )
        val words = content.sections.flatMap { it.paragraphs }.joinToString(" ")
        // F-SCO-08 AC1: not a safety certificate; efficiency is a proxy. F-SCO-07: fairness rules, unusual pace.
        assertTrue("not a safety certificate" in words)
        assertTrue("not a fuel or energy measurement" in words)
        assertTrue("A full, gentle stop always scores at least as well as a rolling one." in words)
        assertTrue("unusual pace can’t set route records" in words)
    }

    @Test
    fun itListsTheThresholdsAndTheScoringVersionWithItsDate() {
        assertEquals(
            listOf("0.30 g or more", "0.28 g or more", "0.36 g or more sideways"),
            content.thresholds.map { it.text },
        )
        assertEquals(EventType.entries, content.thresholds.map { it.type })
        assertEquals("Scoring version smooth-1.0.0 · updated 10 November 2026", content.versionLine)
        assertEquals(85, content.exampleScore)
        assertEquals(83, content.exampleBaseline)
    }
}
