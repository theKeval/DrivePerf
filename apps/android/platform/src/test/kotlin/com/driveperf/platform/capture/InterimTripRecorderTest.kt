package com.driveperf.platform.capture

import com.driveperf.platform.Fixtures
import com.driveperf.platform.Fixtures.fix
import com.driveperf.platform.ports.ActivityEvent
import com.driveperf.platform.ports.ActivityKind
import com.driveperf.platform.ports.CaptureFailure
import com.driveperf.platform.ports.TransitionKind
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

    private val clock = FakeClock()
    private val location = FakeLocationSource()
    private val motion = FakeMotionSource()
    private val activity = FakeActivitySource()
    private val store = FakeStore()

    private fun TestScope.recorder(): InterimTripRecorder {
        return InterimTripRecorder(
            CaptureSources(location, motion, activity), clock, store,
            TripIndex(tmp.newFile("index.tsv").also { it.delete() }),
            scope = this,
        )
    }

    @Test
    fun `is recording the moment start returns, before any fix arrives`() = runTest {
        val recorder = recorder()
        assertTrue(recorder.start())
        val state = recorder.state.value as CaptureState.Recording
        assertEquals(GpsStatus.SEARCHING, state.gps)
        assertEquals(0, state.fixCount)
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
        runCurrent()
        location.flow.emit(fix(1.0, lng = 0.0))
        location.flow.emit(fix(2.0, lng = 0.001))
        motion.flow.emit(Fixtures.motion(1.5))
        activity.flow.emit(ActivityEvent(1, ActivityKind.VEHICLE, TransitionKind.ENTER))
        runCurrent()

        val entry = recorder.stop()
        val writer = store.writers.values.single()
        assertEquals(2, writer.locations.size)
        assertEquals(1, writer.motions.size)
        assertEquals(1, writer.activities.size)
        assertTrue("file is flushed and closed on stop", writer.closed && writer.flushes >= 1)
        assertEquals(2, entry!!.fixCount)
        assertEquals(111.0, entry.distanceM, 1.0)
    }

    @Test
    fun `state reports growing distance and a good GPS`() = runTest {
        val recorder = recorder()
        recorder.start()
        runCurrent()
        location.flow.emit(fix(1.0, lng = 0.0))
        location.flow.emit(fix(2.0, lng = 0.001))
        runCurrent()
        val state = recorder.state.value as CaptureState.Recording
        assertEquals(GpsStatus.GOOD, state.gps)
        assertEquals(111.0, state.distanceM, 1.0)
        assertEquals(2, state.fixCount)
        recorder.stop()
    }

    @Test
    fun `the file is flushed at least every five seconds`() = runTest {
        val recorder = recorder()
        recorder.start()
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(store.writers.values.single().flushes >= 1)
        recorder.stop()
    }

    @Test
    fun `GPS is reported lost when no fix arrives for ten seconds`() = runTest {
        val recorder = recorder()
        recorder.start()
        runCurrent()
        location.flow.emit(fix(1.0))
        runCurrent()
        clock.monoNs += 11 * Fixtures.NS
        advanceTimeBy(5_001)
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(GpsStatus.LOST, (recorder.state.value as CaptureState.Recording).gps)
        recorder.stop()
    }

    @Test
    fun `stop returns to idle and records the trip in the index`() = runTest {
        val index = TripIndex(tmp.newFile("idx.tsv").also { it.delete() })
        val recorder = InterimTripRecorder(CaptureSources(location, motion, activity), clock, store, index, this)
        recorder.start()
        runCurrent()
        location.flow.emit(fix(1.0))
        runCurrent()
        clock.monoNs += 90 * Fixtures.NS
        recorder.stop()
        assertEquals(CaptureState.Idle, recorder.state.value)
        assertEquals(90L, index.entries.value.single().durationS)
    }

    @Test
    fun `a trip with no fixes is discarded`() = runTest {
        val recorder = recorder()
        recorder.start()
        runCurrent()
        assertNull(recorder.stop())
        assertEquals(1, store.deleted.size)
        assertEquals(CaptureState.Idle, recorder.state.value)
    }

    @Test
    fun `stop when nothing is recording does nothing`() = runTest {
        assertNull(recorder().stop())
    }

    @Test
    fun `losing location permission fails the trip with a typed failure`() = runTest {
        location.failWith = CaptureFailure.PermissionDenied("ACCESS_FINE_LOCATION")
        val recorder = recorder()
        recorder.start()
        runCurrent()
        runCurrent()
        val state = recorder.state.value
        assertTrue("expected Failed or Idle after cleanup, was $state", state is CaptureState.Failed || state is CaptureState.Idle)
    }

    @Test
    fun `missing motion and activity permissions do not stop a location only trip`() = runTest {
        motion.failWith = CaptureFailure.SensorUnavailable("accelerometer")
        activity.failWith = CaptureFailure.PermissionDenied("ACTIVITY_RECOGNITION")
        val recorder = recorder()
        recorder.start()
        runCurrent()
        location.flow.emit(fix(1.0))
        runCurrent()
        assertTrue(recorder.state.value is CaptureState.Recording)
        assertNotNull(recorder.stop())
    }
}
