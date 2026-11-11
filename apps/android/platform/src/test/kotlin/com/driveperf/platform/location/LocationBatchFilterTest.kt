package com.driveperf.platform.location

import com.driveperf.platform.Fixtures.fix
import com.driveperf.testing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationBatchFilterTest {
    private val clock = TestClock(monoNs = 100_000_000_000L)

    @Test
    fun `fresh in-order fixes pass`() {
        val filter = LocationBatchFilter(clock)
        assertEquals(2, filter.fresh(listOf(fix(99.0), fix(100.0))).size)
    }

    @Test
    fun `a cached fix from long ago is dropped (spike 1)`() {
        val filter = LocationBatchFilter(clock)
        assertEquals(listOf(100_000_000_000L), filter.fresh(listOf(fix(5.0), fix(100.0))).map { it.tMonoNs })
    }

    @Test
    fun `a fix from before capture started, beyond the allowance, is dropped`() {
        val filter = LocationBatchFilter(clock)
        assertEquals(emptyList<Any>(), filter.fresh(listOf(fix(95.0))))
    }

    @Test
    fun `an out-of-order fix is dropped across batches`() {
        val filter = LocationBatchFilter(clock)
        filter.fresh(listOf(fix(100.0)))
        assertEquals(emptyList<Any>(), filter.fresh(listOf(fix(99.5))))
    }

    @Test
    fun `a new filter starts with a clean ordering state`() {
        LocationBatchFilter(clock).fresh(listOf(fix(100.0)))
        assertEquals(1, LocationBatchFilter(clock).fresh(listOf(fix(99.5))).size)
    }
}
