package com.driveperf.platform.location

import com.driveperf.platform.Fixtures.NS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixFreshnessFilterTest {
    @Test
    fun `accepts a fresh fix`() {
        assertTrue(FixFreshnessFilter().accept(fixMonoNs = 100 * NS, nowMonoNs = 100 * NS + NS / 2))
    }

    @Test
    fun `drops a cached fix delivered long after it was taken`() {
        // Spike 1: every cold launch delivered a fix 504 to 52,106 s old.
        assertFalse(FixFreshnessFilter().accept(fixMonoNs = 100 * NS, nowMonoNs = 700 * NS))
    }

    @Test
    fun `drops a fix from before capture started`() {
        val filter = FixFreshnessFilter(notBeforeNs = 100 * NS)
        assertFalse(filter.accept(fixMonoNs = 95 * NS, nowMonoNs = 101 * NS))
        assertTrue(filter.accept(fixMonoNs = 100 * NS, nowMonoNs = 101 * NS))
    }

    @Test
    fun `drops duplicate and out of order fixes`() {
        val filter = FixFreshnessFilter()
        assertTrue(filter.accept(10 * NS, 11 * NS))
        assertFalse(filter.accept(10 * NS, 11 * NS))
        assertFalse(filter.accept(9 * NS, 11 * NS))
        assertTrue(filter.accept(11 * NS, 12 * NS))
    }

    @Test
    fun `a rejected fix does not move the ordering baseline`() {
        val filter = FixFreshnessFilter(notBeforeNs = 50 * NS)
        assertFalse(filter.accept(40 * NS, 51 * NS))
        assertTrue(filter.accept(50 * NS, 51 * NS))
    }
}
