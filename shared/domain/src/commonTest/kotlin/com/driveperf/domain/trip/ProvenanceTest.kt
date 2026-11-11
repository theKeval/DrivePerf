package com.driveperf.domain.trip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DRIVE-172 (F-DQ-05): the provenance catalogue and the display precision rules. */
class ProvenanceTest {
    @Test
    fun everyMetricHasAUniqueKeyAndAOneLineMeaning() {
        assertEquals(Metric.entries.size, Metric.entries.map { it.key }.toSet().size)
        Metric.entries.forEach {
            assertTrue(it.meaning.isNotBlank() && '\n' !in it.meaning && it.meaning.length <= 100, it.key)
            assertEquals(it, Metric.byKey(it.key))
        }
        assertNull(Metric.byKey("speedScore"))
    }

    @Test
    fun provenanceFollowsPrdSection5() {
        assertEquals(Provenance.DERIVED, Metric.DISTANCE.provenance())
        assertEquals(Provenance.ESTIMATED, Metric.DISTANCE.provenance(includesEstimate = true))
        assertEquals(Provenance.OBSERVED, Metric.START.provenance())
        assertEquals(Provenance.ESTIMATED, Metric.START.provenance(includesEstimate = true))
        assertEquals(Provenance.DERIVED, Metric.SMOOTHNESS.provenance(includesEstimate = true), "never partly estimated")
        assertEquals(Provenance.OBSERVED, Metric.IMU_RATE.provenance())
        assertEquals(Provenance.ESTIMATED, Metric.ESTIMATED_START.provenance())
        assertEquals(Provenance.ESTIMATED, Metric.EFFICIENCY.provenance())
        assertEquals(Provenance.ESTIMATED, Metric.EVENT_CONFIDENCE.provenance())
    }

    @Test
    fun legendWordsMatchTheDesign() {
        assertEquals("Calculated from measured data, the same way every time.", Provenance.DERIVED.meaning)
        assertEquals(listOf("observed", "derived", "estimated"), Provenance.entries.map { it.exportName })
    }

    @Test
    fun trackRowsMapToProvenanceForExports() {
        assertEquals(Provenance.OBSERVED, Provenance.ofTrackQuality(0))
        assertEquals(Provenance.ESTIMATED, Provenance.ofTrackQuality(1))
        assertNull(Provenance.ofTrackQuality(2))
        assertEquals(Provenance.OBSERVED, Provenance.ofTrackQuality(3))
    }

    @Test
    fun numbersNeverShowMoreDecimalsThanTheirAccuracy() {
        assertEquals("39.0", DisplayUnit.KILOMETRES.format(38_950.0))
        assertEquals("0.4", DisplayUnit.KILOMETRES.format(429.0))
        assertEquals("0.42", DisplayUnit.G.format(0.4249))
        assertEquals("−0.43", DisplayUnit.G.format(-0.425))
        assertEquals("0.00", DisplayUnit.G.format(-0.001), "no negative zero")
        assertEquals("103", DisplayUnit.KM_PER_HOUR.format(28.6))
        assertEquals("33", DisplayUnit.MINUTES.format(1_980.0))
        assertEquals("1.4", DisplayUnit.SECONDS.format(1.36))
        assertEquals("97", DisplayUnit.PERCENT.format(96.6))
        assertEquals("50", DisplayUnit.HERTZ.format(49.8))
        assertEquals("118", DisplayUnit.METRES.format(117.5))
        assertEquals("2", DisplayUnit.WHOLE.format(2.0))
        assertEquals(39.0, DisplayUnit.KILOMETRES.round(38_950.0))
        assertEquals(-0.43, DisplayUnit.G.round(-0.425))
        assertEquals(103.0, DisplayUnit.KM_PER_HOUR.round(28.6))
    }
}
