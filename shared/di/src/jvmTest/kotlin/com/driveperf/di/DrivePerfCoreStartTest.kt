package com.driveperf.di

import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.testing.FakeActivitySource
import com.driveperf.testing.FakeLocationSource
import com.driveperf.testing.FakeMotionSource
import com.driveperf.testing.FakeParkedRegionMonitor
import com.driveperf.testing.FakePowerState
import com.driveperf.testing.InMemoryTripFileStore
import com.driveperf.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DrivePerfCoreStartTest {
    private val clock = TestClock()
    private val bindings = object : PlatformBindings {
        val files = InMemoryTripFileStore()
        val location = FakeLocationSource()

        override fun clock() = clock

        override fun location() = location

        override fun motion() = FakeMotionSource()

        override fun activity() = FakeActivitySource()

        override fun power() = FakePowerState()

        override fun parkedRegion() = FakeParkedRegionMonitor()

        override fun tripFiles() = files

        override fun deviceId() = "device-1"
    }

    @AfterTest
    fun reset() = DrivePerfCore.reset()

    @Test
    fun servicesNeedStart() {
        assertFailsWith<IllegalStateException> { DrivePerfCore.services }
    }

    @Test
    fun startWiresAWorkingRecorderOnAnEmptyDatabase() = runBlocking {
        val core = DrivePerfCore.start(bindings)
        assertSame(core, DrivePerfCore.start(bindings))
        core.recorder.onLaunch()
        core.recorder.onWake(WakeEvent(WakeKind.MANUAL, clock.monotonicNs(), clock.wallMs(), "app"))
        withTimeout(5_000) { core.recorder.status.first { it.state == RecorderState.RECORDING } }
        assertTrue(bindings.location.isRunning)
        assertEquals("device-1", bindings.deviceId())
        assertTrue(core.trips.observeTrips().first().isEmpty())
        assertTrue(core.routes.observeRoutes().first().isEmpty())
    }
}
