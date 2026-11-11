package com.driveperf.feature.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The in-drive gate: S-DRV-01/03/04/05, the passenger escape and the 60 s hold after a trip (DRIVE-85/115/120). */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveCoverTest {
    private class Controller : CaptureController {
        override val snapshot = MutableStateFlow<CaptureSnapshot>(CaptureSnapshot.Idle)
        override val canRecord: StateFlow<Boolean> = MutableStateFlow(true)
        val passengerMarks = mutableListOf<Boolean>()
        var stops = 0
        var starts = 0
        var finished: FinishedTrip? = null
        var moving = false
        var watchEnds = 0
        override fun onMovementWatchEnded() { watchEnds++ }
        override fun start() { starts++ }
        override fun stop() { stops++ }
        override fun markPassenger(keep: Boolean) { passengerMarks += keep }
        override suspend fun finishedTrip(tripId: String): FinishedTrip? = finished
        override suspend fun isMoving(sinceWallMs: Long): Boolean = moving
    }

    private val dispatcher = StandardTestDispatcher()
    private val controller = Controller()
    private var wallMs = 1_000_000L

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun TestScope.viewModel(): CaptureViewModel {
        val vm = CaptureViewModel(controller, { now() }, timeLabel = { "5:31 PM" }, carName = { null })
        backgroundScope.launch { vm.cover.collect {} }
        runCurrent()
        return vm
    }

    /** The wall clock follows the test scheduler, so draining idle time (the end of `runTest`) moves it too. */
    private fun TestScope.now() = wallMs + testScheduler.currentTime

    private fun TestScope.recording(trip: String = "t1", manual: Boolean = true, distanceM: Double = 12_400.0) =
        CaptureSnapshot.Recording(now() - 600_000, distanceM, GpsHealth.GOOD, isManual = manual, tripId = trip)

    private fun TestScope.passTime(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun TestScope.finishedTrip(
        manual: Boolean = true,
        endedAgoMs: Long = 0,
        distanceM: Double = 12_400.0,
        durationS: Long = 600,
    ) = FinishedTrip("t1", manual, now() - 600_000, now() - endedAgoMs, distanceM, durationS)

    @Test
    fun `a recording trip locks the app to the in-drive screen with its source line data`() = runTest(dispatcher) {
        val vm = viewModel()
        assertNull(vm.cover.value)
        controller.snapshot.value = recording(manual = false)
        runCurrent()
        val cover = vm.cover.value as DriveCover.InDrive
        assertFalse(cover.state.manual)
        assertEquals("5:31 PM", cover.state.startedAtLabel)
        assertNull("no car name until the Garage exists", cover.state.carName)
        assertEquals(PassengerStep.HIDDEN, cover.state.passengerStep)
        assertTrue(vm.isLocked())
    }

    @Test
    fun `Start without location does not start`() = runTest(dispatcher) {
        val denied = object : CaptureController by controller {
            override val canRecord: StateFlow<Boolean> = MutableStateFlow(false)
        }
        CaptureViewModel(denied, { 0L }).onStart()
        assertEquals(0, controller.starts)
    }

    @Test
    fun `Stop shows Decoding at once, then Drive saved linked to the trip`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        vm.onStopRequested()
        vm.onStopConfirmed()
        runCurrent()
        assertEquals(1, controller.stops)
        assertEquals(DriveCover.Decoding("t1", passenger = false), vm.cover.value)

        controller.finished = finishedTrip()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(1)
        val saved = vm.cover.value as DriveCover.Saved
        assertEquals("t1", saved.tripId)
        assertEquals("12.4", saved.distanceKm)
        assertEquals(10, saved.minutes)
        assertEquals(SavedKind.DRIVE, saved.kind)
        assertFalse("the user stopped it", saved.autoEnded)

        vm.onCoverDismissed()
        runCurrent()
        assertNull(vm.cover.value)
    }

    @Test
    fun `a short trip is labelled as one`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording(distanceM = 300.0)
        runCurrent()
        assertTrue((vm.cover.value as DriveCover.InDrive).state.shortTrip)
        vm.onStopConfirmed()
        controller.finished = finishedTrip(distanceM = 300.0)
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(1)
        assertEquals(SavedKind.SHORT, (vm.cover.value as DriveCover.Saved).kind)
    }

    @Test
    fun `a manual trip that ended after 15 minutes without a Stop says it ended itself`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        controller.finished = finishedTrip(endedAgoMs = 900_000)
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(1)
        assertTrue((vm.cover.value as DriveCover.Saved).autoEnded)
    }

    @Test
    fun `an automatic trip, a user stop and an interrupted trip never get the timeout note`() {
        val timed = FinishedTrip("t", true, 0, 1_000, 5_000.0, 600)
        val now = 1_000 + TripEnd.MANUAL_STOP_MS
        assertTrue(TripEnd.endedByTimeout(timed, userStopped = false, nowWallMs = now))
        assertFalse(TripEnd.endedByTimeout(timed, userStopped = true, nowWallMs = now))
        assertFalse(TripEnd.endedByTimeout(timed.copy(manual = false), userStopped = false, nowWallMs = now))
        assertFalse(TripEnd.endedByTimeout(timed.copy(interrupted = true), userStopped = false, nowWallMs = now))
        assertFalse(TripEnd.endedByTimeout(timed, userStopped = false, nowWallMs = 1_000 + 60_000))
    }

    @Test
    fun `a trip that does not appear in time still gets a saved screen without numbers`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        vm.onStopConfirmed()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(4_000)
        val saved = vm.cover.value as DriveCover.Saved
        assertNull(saved.distanceKm)
        assertEquals("t1", saved.tripId)
    }

    @Test
    fun `an automatic trip the core discarded says it was not saved`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording(manual = false)
        runCurrent()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(4_000)
        assertEquals(DriveCover.NotSaved, vm.cover.value)
    }

    // ---- passenger (DRIVE-120) ----

    @Test
    fun `the passenger sheet offers keep, don't save and cancel`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        vm.onPassengerRequested()
        runCurrent()
        assertEquals(PassengerStep.CHOOSE, (vm.cover.value as DriveCover.InDrive).state.passengerStep)
        vm.onPassengerDismissed()
        runCurrent()
        assertEquals(PassengerStep.HIDDEN, (vm.cover.value as DriveCover.InDrive).state.passengerStep)
        assertTrue("cancel changes nothing", controller.passengerMarks.isEmpty())
    }

    @Test
    fun `keeping a passenger trip tells the core, lifts the lock and shows it saved`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        assertFalse(vm.requestContent(ContentTarget.STORY))
        vm.onPassengerRequested()
        vm.onPassengerKept()
        runCurrent()
        assertEquals(listOf(true), controller.passengerMarks)
        assertEquals(DriveCover.Decoding("t1", passenger = true), vm.cover.value)
        assertFalse("content lock is lifted for a passenger trip", vm.isLocked())

        controller.finished = finishedTrip()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(1)
        assertEquals(SavedKind.PASSENGER, (vm.cover.value as DriveCover.Saved).kind)
        assertFalse("no post-trip hold for a passenger trip", vm.isLocked())
    }

    @Test
    fun `Don't save needs a second confirmation, then the core deletes the trip`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        vm.onPassengerRequested()
        vm.onPassengerDiscardRequested()
        runCurrent()
        assertEquals(PassengerStep.CONFIRM_DISCARD, (vm.cover.value as DriveCover.InDrive).state.passengerStep)
        assertTrue("nothing is deleted by the first tap", controller.passengerMarks.isEmpty())

        vm.onPassengerDiscardConfirmed()
        runCurrent()
        assertEquals(listOf(false), controller.passengerMarks)
        assertEquals(DriveCover.NotSaved, vm.cover.value)
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(4_000)
        assertEquals("still not saved, never a saved screen", DriveCover.NotSaved, vm.cover.value)
        assertFalse(vm.isLocked())
    }

    // ---- content hold (DRIVE-115) ----

    @Test
    fun `content requested during a trip shows S-DRV-05 and offers the passenger escape`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        assertFalse(vm.requestContent(ContentTarget.PAYWALL))
        runCurrent()
        val held = vm.cover.value as DriveCover.Held
        assertEquals("a paywall is never named", HeldNoun.STORY, held.noun)
        assertTrue(held.canMarkPassenger)
        vm.onPassengerRequested()
        runCurrent()
        assertEquals(PassengerStep.CHOOSE, (vm.cover.value as DriveCover.Held).passengerStep)
    }

    @Test
    fun `a Replay is named on the hold screen`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        vm.requestContent(ContentTarget.REPLAY)
        runCurrent()
        assertEquals(HeldNoun.REPLAY, (vm.cover.value as DriveCover.Held).noun)
    }

    @Test
    fun `after the trip the hold lasts until the phone is calm for 60 seconds, then the content opens`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val opened = mutableListOf<ContentTarget>()
            backgroundScope.launch { vm.released.collect { opened += it } }
            controller.snapshot.value = recording()
            runCurrent()
            vm.requestContent(ContentTarget.STORY)
            controller.finished = finishedTrip()
            controller.moving = true
            controller.snapshot.value = CaptureSnapshot.Idle
            passTime(30_000)
            assertTrue("held while still moving", vm.isLocked())
            assertTrue(vm.cover.value is DriveCover.Held)
            assertFalse((vm.cover.value as DriveCover.Held).canMarkPassenger)
            passTime(120_000)
            assertTrue("moving for two minutes still holds", vm.isLocked())
            assertTrue(opened.isEmpty())

            controller.moving = false
            passTime(30_000)
            assertTrue("30 s calm is not enough", vm.isLocked())
            passTime(31_000)
            assertFalse(vm.isLocked())
            assertEquals(listOf(ContentTarget.STORY), opened)
            assertTrue("the saved placeholder shows again", vm.cover.value is DriveCover.Saved)
        }

    @Test
    fun `movement restarts the 60 seconds`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording()
        runCurrent()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(50_000)
        controller.moving = true
        passTime(2_000)
        controller.moving = false
        passTime(50_000)
        assertTrue("only 50 s calm since it moved", vm.isLocked())
        passTime(11_000)
        assertFalse(vm.isLocked())
    }

    @Test
    fun `content opens at once when nothing is locked`() = runTest(dispatcher) {
        val vm = viewModel()
        val opened = mutableListOf<ContentTarget>()
        backgroundScope.launch { vm.released.collect { opened += it } }
        runCurrent()
        assertTrue(vm.requestContent(ContentTarget.ANALYSIS))
        runCurrent()
        assertEquals(listOf(ContentTarget.ANALYSIS), opened)
    }

    @Test
    fun `a new trip clears the old saved screen`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.snapshot.value = recording("t1")
        runCurrent()
        vm.onStopConfirmed()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(4_000)
        assertTrue(vm.cover.value is DriveCover.Saved)
        controller.snapshot.value = recording("t2")
        passTime(1)
        assertTrue(vm.cover.value is DriveCover.InDrive)
    }

    @Test
    fun `a paywall or share prompt held during the trip is dropped, not reopened`() = runTest(dispatcher) {
        val vm = viewModel()
        val opened = mutableListOf<ContentTarget>()
        backgroundScope.launch { vm.released.collect { opened += it } }
        controller.snapshot.value = recording()
        runCurrent()
        vm.requestContent(ContentTarget.PAYWALL)
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(61_000)
        assertFalse(vm.isLocked())
        assertTrue("the paywall is not reopened", opened.isEmpty())
    }

    @Test
    fun `the hold is capped when the phone never looks calm, and the watch ends`() = runTest(dispatcher) {
        val vm = viewModel()
        controller.moving = true
        controller.snapshot.value = recording()
        runCurrent()
        controller.snapshot.value = CaptureSnapshot.Idle
        passTime(300_000)
        assertTrue(vm.isLocked())
        passTime(301_000)
        assertFalse("a stale automotive reading cannot hold content for ever", vm.isLocked())
        assertEquals(1, controller.watchEnds)
    }
}
