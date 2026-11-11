package com.driveperf.platform.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.LocationSink
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.PermissionKind
import com.driveperf.platform.Fixtures
import com.driveperf.platform.Fixtures.fix
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class InterimTripRecorderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = TestClock()
    private val location = FakeLocationSource()
    private val motion = FakeMotionSource()
    private val activity = FakeActivitySource()
    private val failures = CaptureFailureBus()
    private val files = CountingStore()
    private lateinit var index: TripIndex

    /** An in-memory store that also counts syncs, so the 5 s rule can be checked. */
    private class CountingStore(val inner: InMemoryTripFileStore = InMemoryTripFileStore()) : TripFileStore by inner {
        var syncs = 0
        override fun sync(tripId: String, kind: TripFileKind): FileResult = inner.sync(tripId, kind).also { syncs++ }
    }

    private fun TestScope.recorder(
        store: TripFileStore = files,
        locationSource: LocationSource = location,
    ): InterimTripRecorder {
        index = TripIndex(tmp.newFile().also { it.delete() })
        return InterimTripRecorder(CaptureSources(locationSource, motion, activity, failures), clock, store, index, this)
    }

    private fun lines(): List<String> =
        files.inner.tripIds().single().let { files.inner.read(it, TripFileKind.R0)!!.decodeToString().lines().filter(String::isNotEmpty) }

    @Test
    fun `is recording the moment start returns, with all three sources running`() = runTest {
        val recorder = recorder()
        assertTrue(recorder.start())
        val state = recorder.state.value as CaptureState.Recording
        assertEquals(GpsStatus.SEARCHING, state.gps)
        assertEquals(0, state.fixCount)
        assertTrue(location.isRunning && motion.rateHz == 50 && activity.isRunning)
        recorder.stop()
    }

    @Test
    fun `a second start while recording is refused`() = runTest {
        val recorder = recorder()
        assertTrue(recorder.start())
        assertFalse(recorder.start())
        recorder.stop()
    }

    @Test
    fun `every location, motion and activity sample reaches the trip file`() = runTest {
        val recorder = recorder()
        recorder.start()
        location.emit(fix(1.0, lng = 0.0), fix(2.0, lng = 0.001))
        motion.emit(Fixtures.motion(1.5))
        activity.emit(ActivitySample(1, 2, ActivityType.AUTOMOTIVE, ActivityConfidence.HIGH))
        val entry = recorder.stop()

        val written = lines()
        assertEquals("# driveperf-r0-interim v0", written[0])
        assertEquals(2, written.count { it.startsWith("L,") })
        assertEquals(1, written.count { it.startsWith("M,") })
        assertEquals(1, written.count { it.startsWith("A,") })
        assertEquals(2, entry!!.fixCount)
        assertEquals(111.0, entry.distanceM, 1.0)
    }

    @Test
    fun `state reports growing distance and a good GPS`() = runTest {
        val recorder = recorder()
        recorder.start()
        location.emit(fix(1.0, lng = 0.0), fix(2.0, lng = 0.001))
        val state = recorder.state.value as CaptureState.Recording
        assertEquals(GpsStatus.GOOD, state.gps)
        assertEquals(111.0, state.distanceM, 1.0)
        assertEquals(2, state.fixCount)
        recorder.stop()
    }

    @Test
    fun `the file is synced at least every five seconds`() = runTest {
        val recorder = recorder()
        recorder.start()
        runCurrent()
        val before = files.syncs
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(files.syncs > before)
        recorder.stop()
    }

    @Test
    fun `GPS is reported lost when no fix arrives for ten seconds`() = runTest {
        val recorder = recorder()
        recorder.start()
        location.emit(fix(1.0))
        clock.advanceS(11.0)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(GpsStatus.LOST, (recorder.state.value as CaptureState.Recording).gps)
        recorder.stop()
    }

    @Test
    fun `stop returns to idle, stops every source and records the trip in the index`() = runTest {
        val recorder = recorder()
        recorder.start()
        location.emit(fix(1.0))
        clock.advanceS(90.0)
        recorder.stop()
        assertEquals(CaptureState.Idle, recorder.state.value)
        assertFalse(location.isRunning || activity.isRunning || motion.rateHz != null)
        assertEquals(90L, index.entries.value.single().durationS)
    }

    @Test
    fun `samples that arrive after stop are not written`() = runTest {
        val lingering = LingeringLocationSource()
        val recorder = recorder(locationSource = lingering)
        recorder.start()
        lingering.deliver(fix(1.0))
        val tripId = files.inner.tripIds().single()
        recorder.stop()
        val sizeAfterStop = files.inner.read(tripId, TripFileKind.R0)!!.size
        lingering.deliver(fix(2.0)) // a fix already in flight when the provider was stopped
        assertEquals(sizeAfterStop, files.inner.read(tripId, TripFileKind.R0)!!.size)
        assertEquals(CaptureState.Idle, recorder.state.value)
    }

    @Test
    fun `a trip with no fixes is discarded`() = runTest {
        val recorder = recorder()
        recorder.start()
        assertNull(recorder.stop())
        assertTrue(files.inner.tripIds().isEmpty())
        assertEquals(CaptureState.Idle, recorder.state.value)
    }

    @Test
    fun `stop when nothing is recording does nothing`() = runTest {
        assertNull(recorder().stop())
    }

    @Test
    fun `losing location permission ends the trip with a typed failure`() = runTest {
        val recorder = recorder()
        recorder.start()
        location.emit(fix(1.0))
        failures.report(DomainError.Capture.PermissionDenied(PermissionKind.LOCATION))
        runCurrent()
        val state = recorder.state.value
        assertTrue("expected Failed, was $state", state is CaptureState.Failed)
        assertEquals(DomainError.Capture.PermissionDenied(PermissionKind.LOCATION), (state as CaptureState.Failed).error)
        assertFalse(location.isRunning)
        assertEquals("what was recorded is kept", 1, index.entries.value.size)
    }

    @Test
    fun `missing motion and activity permissions do not stop a location only trip`() = runTest {
        val recorder = recorder()
        recorder.start()
        failures.report(DomainError.Capture.SensorUnavailable)
        failures.report(DomainError.Capture.PermissionDenied(PermissionKind.MOTION))
        runCurrent()
        location.emit(fix(1.0))
        assertTrue(recorder.state.value is CaptureState.Recording)
        assertNotNull(recorder.stop())
    }

    @Test
    fun `a full disk ends the trip with the storage error`() = runTest {
        val recorder = recorder(DiskFullAfterHeader())
        recorder.start()
        location.emit(fix(1.0))
        runCurrent()
        assertEquals(CaptureState.Failed(DomainError.Storage.DiskFull), recorder.state.value)
    }

    /** A source that keeps its sink after stop, like a provider callback that is still running when it is removed. */
    private class LingeringLocationSource : LocationSource {
        private var sink: LocationSink? = null
        override fun start(sink: LocationSink) {
            this.sink = sink
        }
        override fun stop() = Unit
        fun deliver(sample: LocationSample) = sink!!.onLocations(listOf(sample))
    }

    /** Accepts the header, then reports a full disk for everything else. */
    private class DiskFullAfterHeader(inner: InMemoryTripFileStore = InMemoryTripFileStore()) : TripFileStore by inner {
        private var writes = 0
        override fun append(tripId: String, kind: TripFileKind, bytes: ByteArray) =
            if (writes++ == 0) FileResult.OK else FileResult.DISK_FULL
    }
}
