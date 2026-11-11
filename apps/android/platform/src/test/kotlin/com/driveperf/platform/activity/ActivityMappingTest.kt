package com.driveperf.platform.activity

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.google.android.gms.location.DetectedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityMappingTest {
    @Test
    fun `Play services activity types map to the neutral types`() {
        assertEquals(ActivityType.AUTOMOTIVE, activityTypeOf(DetectedActivity.IN_VEHICLE))
        assertEquals(ActivityType.CYCLING, activityTypeOf(DetectedActivity.ON_BICYCLE))
        assertEquals(ActivityType.WALKING, activityTypeOf(DetectedActivity.WALKING))
        assertEquals(ActivityType.WALKING, activityTypeOf(DetectedActivity.ON_FOOT))
        assertEquals(ActivityType.RUNNING, activityTypeOf(DetectedActivity.RUNNING))
        assertEquals(ActivityType.STATIONARY, activityTypeOf(DetectedActivity.STILL))
        assertEquals(ActivityType.UNKNOWN, activityTypeOf(DetectedActivity.TILTING))
        assertEquals(ActivityType.UNKNOWN, activityTypeOf(DetectedActivity.UNKNOWN))
    }

    @Test
    fun `confidence percent buckets into low, medium and high`() {
        assertEquals(ActivityConfidence.LOW, confidenceOf(0))
        assertEquals(ActivityConfidence.LOW, confidenceOf(49))
        assertEquals(ActivityConfidence.MEDIUM, confidenceOf(50))
        assertEquals(ActivityConfidence.MEDIUM, confidenceOf(74))
        assertEquals(ActivityConfidence.HIGH, confidenceOf(75))
        assertEquals(ActivityConfidence.HIGH, confidenceOf(100))
    }

    private fun reading(tNs: Long, type: ActivityType, confidence: ActivityConfidence) =
        ActivitySample(tNs, tNs / 1_000_000, type, confidence)

    @Test
    fun `only a change of type or confidence level is emitted, stamped with when it was first seen`() {
        val filter = ActivityChangeFilter()
        val first = reading(1_000, ActivityType.STATIONARY, ActivityConfidence.HIGH)
        assertEquals(first, filter.onReading(first))
        assertNull("same state again", filter.onReading(reading(6_000, ActivityType.STATIONARY, ActivityConfidence.HIGH)))
        val driving = reading(11_000, ActivityType.AUTOMOTIVE, ActivityConfidence.MEDIUM)
        assertEquals(driving, filter.onReading(driving))
        val surer = reading(16_000, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH)
        assertEquals("a confidence change counts", surer, filter.onReading(surer))
    }

    @Test
    fun `history answers exactly the window asked, oldest first, and keeps only the most recent`() {
        val history = ActivityHistory(capacity = 3)
        (1L..4L).forEach { history.add(ActivitySample(it, it * 1000, ActivityType.WALKING, ActivityConfidence.LOW)) }
        assertEquals("the oldest was evicted", listOf(2L, 3L, 4L), history.between(0, 10_000).map { it.tMonoNs })
        assertEquals(listOf(3L), history.between(3000, 3000).map { it.tMonoNs })
        assertEquals("never padded", emptyList<ActivitySample>(), history.between(5000, 6000))
    }
}
