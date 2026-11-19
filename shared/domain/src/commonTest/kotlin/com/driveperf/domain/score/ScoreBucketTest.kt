package com.driveperf.domain.score

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreBucketTest {
    @Test
    fun scoresFallIntoTheSixBucketsOfTheAnalyticsSpec() {
        val buckets = listOf(0, 49, 50, 59, 60, 69, 70, 79, 80, 89, 90, 100).map { scoreBucket(it) }
        assertEquals(
            listOf("lt_50", "lt_50", "50_59", "50_59", "60_69", "60_69", "70_79", "70_79", "80_89", "80_89", "90_100", "90_100"),
            buckets,
        )
        assertNull(scoreBucket(null), "no score, no bucket")
    }

    @Test
    fun theNoOpRecomputeDoesNothing() = runTest {
        TripRecompute.NONE.fromTrip("any")
    }
}
