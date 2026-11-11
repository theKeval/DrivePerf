package com.driveperf.platform.location

import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.LocationSink
import com.driveperf.domain.capture.LocationSource
import com.driveperf.platform.Fixtures
import com.driveperf.testing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixWatchTest {
    private val clock = TestClock()
    private var sink: LocationSink? = null
    private var stopped = false
    private val delegate = object : LocationSource {
        override fun start(sink: LocationSink) {
            this@FixWatchTest.sink = sink
        }

        override fun stop() {
            stopped = true
        }
    }

    @Test
    fun `searching until a fix, good while fresh, lost after ten seconds, searching again on restart`() {
        val watch = FixWatch(delegate, clock)
        val delivered = mutableListOf<LocationSample>()
        watch.start { delivered += it }
        assertEquals(GpsStatus.SEARCHING, watch.status())
        clock.advanceS(100.0)
        val fix = Fixtures.fix(100.0)
        sink!!.onLocations(listOf(fix))
        assertEquals(listOf(fix), delivered)
        assertEquals(GpsStatus.GOOD, watch.status())
        clock.advanceS(11.0)
        assertEquals(GpsStatus.LOST, watch.status())
        watch.stop()
        assertTrue(stopped)
        watch.start {}
        assertEquals(GpsStatus.SEARCHING, watch.status())
    }
}
