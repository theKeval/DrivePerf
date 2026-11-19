package com.driveperf.pipeline.score

import com.driveperf.domain.trip.TripKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * DRIVE-131 AC1's 2 s budget, on the JVM only: a wall-clock bound is too noisy for debug Kotlin/Native on a shared CI
 * simulator. The common `HistoryRecomputeTest` checks the work stays bounded on every platform.
 */
class HistoryRecomputeBudgetTest {
    @Test
    fun aLongHistoryRecomputesWellInsideTheTwoSecondBudget() = runTest {
        val w = RecomputeWorld().longHistory()
        w.setKind("t1000", TripKind.PASSENGER)
        val mark = TimeSource.Monotonic.markNow()
        w.recompute.fromTrip("t1000")
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "1,000 later trips over 1,000 earlier ones took $elapsed")
        assertNull(w.score("t1000").smoothness)
    }
}
