package com.driveperf.pipeline.score

import com.driveperf.domain.trip.EventType
import com.driveperf.pipeline.fixtures.LabelledFixtures
import com.driveperf.pipeline.fixtures.PipelineHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DRIVE-93 AC2: the precision and recall harness, and the synthetic labelled set meeting the Q20 targets. */
class EventHarnessTest {
    private fun e(type: EventType, t: Double) = LabelledEvent(type, t)

    @Test
    fun matchingIsOneToOneNearestFirstAndPerType() {
        val labels = listOf(e(EventType.BRAKE, 10.0), e(EventType.BRAKE, 11.5), e(EventType.CORNER, 20.0))
        val detected = listOf(e(EventType.BRAKE, 10.9), e(EventType.BRAKE, 30.0), e(EventType.ACCEL, 20.0))

        val counts = EventHarness.evaluate(detected, labels)

        // 10.9 is 0.6 s from 11.5 and 0.9 s from 10.0: it takes the nearer label, and 10.0 stays unmatched.
        assertEquals(TypeCounts(1, 1, 1), counts[EventType.BRAKE])
        assertEquals(TypeCounts(0, 1, 0), counts[EventType.ACCEL], "the wrong type never matches")
        assertEquals(TypeCounts(0, 0, 1), counts[EventType.CORNER])
        assertEquals(0.5, counts.getValue(EventType.BRAKE).precision)
        assertEquals(0.0, counts.getValue(EventType.CORNER).recall)
        assertNull(counts.getValue(EventType.CORNER).precision, "nothing detected: no precision to judge")
        val apart = EventHarness.evaluate(listOf(e(EventType.BRAKE, 11.01)), listOf(e(EventType.BRAKE, 10.0)))
        assertEquals(TypeCounts(0, 1, 1), apart[EventType.BRAKE], "more than 1.0 s apart is no match")
    }

    @Test
    fun failuresNameEachTypeBelowATarget() {
        val counts = mapOf(
            EventType.BRAKE to TypeCounts(17, 3, 0),
            EventType.ACCEL to TypeCounts(3, 0, 2),
            EventType.CORNER to TypeCounts(0, 0, 0),
        )
        val failures = EventHarness.failures(counts)
        assertEquals(1, failures.size, failures.toString())
        assertEquals(true, failures.single().startsWith("ACCEL recall"))
        assertEquals(TypeCounts(34, 6, 0), EventHarness.total(listOf(counts, counts))[EventType.BRAKE])
    }

    @Test
    fun labelsRoundTrip() {
        val labels = listOf(e(EventType.CORNER, 47.25), e(EventType.BRAKE, 9.5))
        val text = EventHarness.writeLabels("x", labels)
        assertEquals("# privacy-trimmed\n# id: x\ntype,t_peak_s\nBRAKE,9.50\nCORNER,47.25\n", text)
        assertEquals(labels.sortedBy { it.tPeakS }, EventHarness.parseLabels(text))
    }

    @Test
    fun uncertainLabelsCountNeitherWay() {
        val sure = e(EventType.BRAKE, 10.0)
        val unsure = LabelledEvent(EventType.BRAKE, 20.0, uncertain = true)
        val missed = LabelledEvent(EventType.BRAKE, 40.0, uncertain = true)
        val detected = listOf(e(EventType.BRAKE, 10.2), e(EventType.BRAKE, 20.3), e(EventType.BRAKE, 30.0))

        val counts = EventHarness.evaluate(detected, listOf(sure, unsure, missed)).getValue(EventType.BRAKE)

        // 10.2 matches the sure label; 20.3 matches the uncertain one and is set aside; 30.0 is a false positive; the
        // missed uncertain label at 40.0 is no false negative.
        assertEquals(TypeCounts(1, 1, 0), counts)
        // An uncertain label still takes part in nearest-first matching: 20.6 is nearer the uncertain 20.0 than the
        // sure 21.5, so it is set aside and the sure label is missed.
        val nearer = EventHarness.evaluate(
            listOf(e(EventType.BRAKE, 20.6)),
            listOf(unsure, e(EventType.BRAKE, 21.5)),
        ).getValue(EventType.BRAKE)
        assertEquals(TypeCounts(0, 0, 1), nearer)
    }

    @Test
    fun detectionsAndLabelsOutsideTheCoverageAreIgnored() {
        val coverage = Coverage(listOf(5.0..50.0, 80.0..90.0))
        val labels = listOf(e(EventType.CORNER, 2.0), e(EventType.CORNER, 30.0), e(EventType.CORNER, 85.0))
        val detected = listOf(e(EventType.CORNER, 30.4), e(EventType.CORNER, 60.0), e(EventType.CORNER, 100.0))

        val counts = EventHarness.evaluate(detected, labels, coverage = coverage).getValue(EventType.CORNER)

        assertEquals(TypeCounts(1, 0, 1), counts)
        assertEquals(TypeCounts(1, 2, 2), EventHarness.evaluate(detected, labels).getValue(EventType.CORNER))
    }

    @Test
    fun typesWithTooFewConfirmedLabelsAreInsufficientNotFailures() {
        val counts = mapOf(
            EventType.BRAKE to TypeCounts(2, 3, 1),
            EventType.ACCEL to TypeCounts(6, 4, 4),
            EventType.CORNER to TypeCounts(12, 0, 1),
        )
        assertEquals(listOf(EventType.BRAKE), EventHarness.insufficient(counts))
        val failures = EventHarness.failures(counts, EventHarness.MIN_LABELS)
        assertEquals(listOf("ACCEL precision", "ACCEL recall"), failures.map { it.substringBefore(" 0.") })
        assertEquals(4, EventHarness.failures(counts).size, "without a minimum every type is judged")
    }

    @Test
    fun wilsonIntervalsMatchTheTextbook() {
        val i = checkNotNull(EventHarness.wilson95(8, 10))
        assertEquals(0.490, i.start, 0.001)
        assertEquals(0.943, i.endInclusive, 0.001)
        assertEquals(1.0, checkNotNull(EventHarness.wilson95(5, 5)).endInclusive)
        assertNull(EventHarness.wilson95(0, 0))
    }

    @Test
    fun uncertainLabelsAndCoverageRoundTrip() {
        val labels = listOf(LabelledEvent(EventType.ACCEL, 12.0, uncertain = true), e(EventType.BRAKE, 9.5))
        val coverage = Coverage(listOf(0.0..100.25))
        val text = EventHarness.writeLabels("a19-x", labels, coverage)
        assertEquals(
            "# privacy-trimmed\n# id: a19-x\n# covered_s: 0.00 100.25\n" +
                "type,t_peak_s,certainty\nBRAKE,9.50,certain\nACCEL,12.00,uncertain\n",
            text,
        )
        assertEquals(labels.sortedBy { it.tPeakS }, EventHarness.parseLabels(text))
        assertEquals(coverage, EventHarness.parseCoverage(text))
        assertEquals(Coverage(), EventHarness.parseCoverage(EventHarness.writeLabels("x", labels)))
    }

    @Test
    fun theSyntheticLabelledSetMeetsTheTargets() = runTest {
        val harness = PipelineHarness()
        val counts = EventHarness.total(
            LabelledFixtures.all.map { l ->
                EventHarness.evaluate(EventHarness.of(harness.trip(l.fixture.capture()).events), l.labels)
            },
        )
        assertEquals(emptyList(), EventHarness.failures(counts), counts.toString())
        EventType.entries.forEach { assertEquals(3, counts.getValue(it).truePositives, "$it: every hard manoeuvre is found") }
    }
}
