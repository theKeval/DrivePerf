package com.driveperf.platform

import com.driveperf.platform.Fixtures.NS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessFilterTest {
    @Test
    fun `accepts a fresh fix`() {
        assertTrue(FreshnessFilter().accept(sampleMonoNs = 100 * NS, nowMonoNs = 100 * NS + NS / 2))
    }

    @Test
    fun `drops a cached fix delivered long after it was taken`() {
        // Spike 1: every cold launch delivered a fix 504 to 52,106 s old.
        assertFalse(FreshnessFilter().accept(sampleMonoNs = 100 * NS, nowMonoNs = 700 * NS))
    }

    @Test
    fun `drops a fix from before capture started`() {
        val filter = FreshnessFilter(notBeforeNs = 100 * NS)
        assertFalse(filter.accept(sampleMonoNs = 95 * NS, nowMonoNs = 101 * NS))
        assertTrue(filter.accept(sampleMonoNs = 100 * NS, nowMonoNs = 101 * NS))
    }

    @Test
    fun `drops a sample timestamped far in the future or at zero`() {
        val filter = FreshnessFilter()
        assertFalse(filter.accept(sampleMonoNs = 500 * NS, nowMonoNs = 100 * NS))
        assertFalse(filter.accept(sampleMonoNs = 0, nowMonoNs = 100 * NS))
        assertTrue(filter.accept(sampleMonoNs = 100 * NS, nowMonoNs = 100 * NS))
    }

    @Test
    fun `motion uses a tighter age limit`() {
        val filter = FreshnessFilter(maxAgeNs = FreshnessFilter.MOTION_MAX_AGE_NS)
        assertFalse(filter.accept(sampleMonoNs = 100 * NS, nowMonoNs = 103 * NS))
        assertTrue(filter.accept(sampleMonoNs = 102 * NS, nowMonoNs = 103 * NS))
    }

    @Test
    fun `drops duplicate and out of order fixes`() {
        val filter = FreshnessFilter()
        assertTrue(filter.accept(10 * NS, 11 * NS))
        assertFalse(filter.accept(10 * NS, 11 * NS))
        assertFalse(filter.accept(9 * NS, 11 * NS))
        assertTrue(filter.accept(11 * NS, 12 * NS))
    }

    @Test
    fun `a rejected fix does not move the ordering baseline`() {
        val filter = FreshnessFilter(notBeforeNs = 50 * NS)
        assertFalse(filter.accept(40 * NS, 51 * NS))
        assertTrue(filter.accept(50 * NS, 51 * NS))
    }
}
