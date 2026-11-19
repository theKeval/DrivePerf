package com.driveperf.feature.replay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** DRIVE-39: an absent R1 sample (NaN) is a gap in the S-RPL-02 trace and the S-SCO-04 sparkline, not a broken line. */
class EvidenceChartTest {
    @Test
    fun anAbsentSampleSplitsTheLineAndIsLeftOut() {
        val points = listOf(Offset(0f, 1f), Offset(1f, 2f), Offset(2f, Float.NaN), Offset(3f, 4f), Offset(4f, 5f))
        assertEquals(listOf(listOf(Offset(0f, 1f), Offset(1f, 2f)), listOf(Offset(3f, 4f), Offset(4f, 5f))), finiteRuns(points))
    }

    @Test
    fun leadingTrailingAndRepeatedGapsLeaveNoEmptyRuns() {
        val nan = Offset(0f, Float.NaN)
        assertEquals(listOf(listOf(Offset(1f, 1f))), finiteRuns(listOf(nan, nan, Offset(1f, 1f), nan)))
        assertEquals(emptyList<List<Offset>>(), finiteRuns(listOf(nan)))
    }
}
