package com.driveperf.platform.wake

import android.content.Intent
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.platform.capture.CaptureStarter
import com.driveperf.testing.TestClock
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.DetectedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakePathMappingTest {
    private val clock = TestClock().apply { advanceS(1_000.0) }

    @Test
    fun `a vehicle entry becomes a high-confidence sample and an ACTIVITY_VEHICLE wake at its own time`() {
        val t = clock.monotonicNs() - 4_000_000_000L
        val enter = ActivityTransitionEvent(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER, t)
        val sample = ActivityTransitions.sampleOf(enter, clock)!!
        assertEquals(ActivityType.AUTOMOTIVE, sample.type)
        assertEquals(ActivityConfidence.HIGH, sample.confidence)
        assertEquals(t, sample.tMonoNs)
        assertEquals(clock.wallMs() - 4_000, sample.wallMs)
        val wake = WakeEvent(WakeKind.ACTIVITY_VEHICLE, t, sample.wallMs, "activity_transition")
        assertEquals(wake, ActivityTransitions.wakeOf(listOf(sample)))
    }

    @Test
    fun `exits are not samples and walking does not wake`() {
        val exit = ActivityTransitionEvent(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT, 5L)
        assertNull(ActivityTransitions.sampleOf(exit, clock))
        val walk = ActivityTransitionEvent(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER, 5L)
        assertNull(ActivityTransitions.wakeOf(listOfNotNull(ActivityTransitions.sampleOf(walk, clock))))
    }

    @Test
    fun `every watched activity is registered for entry only`() {
        assertEquals(5, ActivityTransitions.TRANSITIONS.size)
        val types = ActivityTransitions.TRANSITIONS.map { it.transitionType }.toSet()
        assertEquals(setOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER), types)
    }

    @Test
    fun `a parked-region exit wakes at the triggering fix, or now without one`() {
        assertEquals(
            WakeEvent(WakeKind.PARKED_EXIT, 42L, 7L, "geofence"),
            GeofenceParkedRegionMonitor.wakeOf(exit = true, fixMonoNs = 42L, fixWallMs = 7L, clock = clock),
        )
        val now = GeofenceParkedRegionMonitor.wakeOf(exit = true, fixMonoNs = null, fixWallMs = null, clock = clock)!!
        assertEquals(clock.monotonicNs(), now.tMonoNs)
        assertEquals(clock.wallMs(), now.wallMs)
        assertNull(GeofenceParkedRegionMonitor.wakeOf(exit = false, fixMonoNs = 1L, fixWallMs = 1L, clock = clock))
    }

    @Test
    fun `a wake survives the trip through the service intent`() {
        val wake = WakeEvent(WakeKind.MANUAL, 123L, 456L, "app")
        val intent = Intent().putExtra("kind", "MANUAL").putExtra("t_mono_ns", 123L).putExtra("wall_ms", 456L)
            .putExtra("detail", "app")
        assertEquals(wake, CaptureStarter.wakeOf(intent))
        assertNull(CaptureStarter.wakeOf(Intent()))
    }

    @Test
    fun `dispatch reaches the handler, and nothing without one`() {
        val seen = mutableListOf<WakeEvent>()
        val wake = WakeEvent(WakeKind.PARKED_EXIT, 1L, 2L, null)
        WakeDispatcher.setHandler(null)
        WakeDispatcher.dispatch(wake)
        WakeDispatcher.setHandler { seen += it }
        WakeDispatcher.dispatch(wake)
        WakeDispatcher.setHandler(null)
        assertEquals(listOf(wake), seen)
    }

    @Test
    fun `the boot receiver only acts on the broadcasts it is declared for`() {
        val receiver = BootReceiver()
        receiver.onReceive(org.robolectric.RuntimeEnvironment.getApplication(), Intent("spoofed"))
        assertNull(receiver.lastHandled)
        receiver.onReceive(org.robolectric.RuntimeEnvironment.getApplication(), Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(Intent.ACTION_BOOT_COMPLETED, receiver.lastHandled)
    }

    @Test
    fun `a parked region Play services rejected is registered again until it succeeds`() {
        val memory = ParkedRegionMemory()
        assertNull("nothing armed yet", memory.pending())
        val spot = com.driveperf.domain.capture.GeoPoint(2.5, -27.5)
        memory.armed(spot, 120.0)
        memory.failed(spot) // no background location yet
        val expected: Any = spot to 120.0
        assertEquals(expected, memory.pending())
        memory.registered(com.driveperf.domain.capture.GeoPoint(1.0, -26.0))
        assertEquals("a result for an older region does not count", expected, memory.pending())
        memory.registered(spot)
        assertNull("registered: nothing to redo", memory.pending())
        memory.disarmed()
        assertNull(memory.pending())
    }
}
