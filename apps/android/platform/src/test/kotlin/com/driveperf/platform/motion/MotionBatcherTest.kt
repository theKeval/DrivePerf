package com.driveperf.platform.motion

import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.MotionSink
import com.driveperf.platform.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionBatcherTest {
    private val batches = mutableListOf<List<MotionSample>>()
    private val batcher = MotionBatcher(MotionSink { batches += it }, windowNs = 200_000_000L)

    @Test
    fun `samples are held until the batch spans the window, then handed over in order`() {
        (0..9).forEach { batcher.add(Fixtures.motion(1.0 + it * 0.02)) } // 0 to 180 ms
        assertEquals(0, batches.size)
        batcher.add(Fixtures.motion(1.2)) // 200 ms: the window is full
        assertEquals(1, batches.size)
        assertEquals(11, batches[0].size)
        assertEquals(batches[0].map { it.tMonoNs }, batches[0].map { it.tMonoNs }.sorted())
    }

    @Test
    fun `the next batch starts from the next sample`() {
        (0..10).forEach { batcher.add(Fixtures.motion(1.0 + it * 0.02)) }
        (0..10).forEach { batcher.add(Fixtures.motion(2.0 + it * 0.02)) }
        assertEquals(listOf(11, 11), batches.map { it.size })
    }

    @Test
    fun `at a low rate each sample can be a batch of its own`() {
        batcher.add(Fixtures.motion(1.0))
        batcher.add(Fixtures.motion(1.5))
        assertEquals(listOf(2), batches.map { it.size })
    }
}
