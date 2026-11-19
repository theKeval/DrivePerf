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
        assertEquals("# id: x\ntype,t_peak_s\nBRAKE,9.50\nCORNER,47.25\n", text)
        assertEquals(labels.sortedBy { it.tPeakS }, EventHarness.parseLabels(text))
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
