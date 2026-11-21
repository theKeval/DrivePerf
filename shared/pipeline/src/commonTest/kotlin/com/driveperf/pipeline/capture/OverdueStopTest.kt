package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.ThermalState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.capture.CaptureFixtures.activity
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.motionRun
import com.driveperf.pipeline.capture.CaptureFixtures.ns
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.pipeline.capture.CaptureFixtures.wallAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DRIVE-223: the platform's stop timer can stall for hours in deep sleep, but every event carries a clock that kept
 * counting. A paused trip whose stop is overdue ends at its stop (F-CAP-03 AC1) before the event is handled, so the
 * next drive is a trip of its own; a pull-away before the deadline still continues the same trip (AC2).
 */
class OverdueStopTest {
    private val m = TripMachine()
    private val idle = MachineState(parking = parking)

    /** The 2 h 11 min park of the field report, after the stop began at 31 s. */
    private val afterParkS = 31.0 + 2 * 3600 + 11 * 60

    private fun wake(s: MachineState, atS: Double, kind: WakeKind = WakeKind.PARKED_EXIT, id: String = "trip-1") =
        m.reduce(s, MachineEvent.Wake(ns(atS), wallAt(atS), WakeEvent(kind, ns(atS), wallAt(atS)), header(atS, kind, id = id)))

    private fun MachineState.fixes(vararg f: LocationSample, nowS: Double = f.last().tMonoNs / 1e9) =
        m.reduce(this, MachineEvent.Fixes(ns(nowS), f.toList()))

    private fun drive(s: MachineState, fromS: Int, toS: Int, speed: Double, startNorth: Double = 0.0): Transition {
        var t = Transition(s)
        val effects = mutableListOf<MachineEffect>()
        for (i in fromS..toS) {
            t = t.state.fixes(fix(i.toDouble(), speed, startNorth + (i - fromS) * speed))
            effects += t.effects
        }
        return Transition(t.state, effects)
    }

    /** An automatic trip that drove to 30 s and stopped at 31 s: paused, its stop timer due at 271 s. */
    private fun parked(): MachineState {
        val moving = drive(wake(idle, 0.0).state, 1, 30, 10.0).state
        val s = drive(moving, 31, 65, 0.0, startNorth = 300.0).state
        assertEquals(RecorderState.PAUSED, s.phase)
        assertEquals(ns(31.0), s.stoppingBeganAtNs)
        return s
    }

    private fun Transition.finalise(): MachineEffect.Finalise = effects.filterIsInstance<MachineEffect.Finalise>().single()

    private fun Transition.assertEndedAtTheStop(tripId: String = "trip-1") {
        assertEquals(RecorderState.FINISHING, state.phase)
        val end = finalise()
        assertEquals(tripId, end.tripId)
        assertEquals(ns(31.0), end.endNs, "the trip ends when stopping began, as the stop timer would have ended it")
        assertEquals(EndReason.STOP_TIMER, end.reason)
        assertFalse(effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.RESUMED })
    }

    @Test
    fun theStopTimerNeverFiredAndTheNextDriveWakesTheAppTwoHoursLater() {
        val woke = wake(parked(), afterParkS, WakeKind.ACTIVITY_VEHICLE, id = "trip-2")
        woke.assertEndedAtTheStop()
        assertNotNull(woke.state.pendingWake, "the wake that ended the last trip starts the next one")

        val pullAway = woke.state.fixes(fix(afterParkS + 1, 8.0, 400.0))
        assertEquals(RecorderState.FINISHING, pullAway.state.phase, "a finishing trip never resumes")
        assertTrue(pullAway.effects.isEmpty())

        val end = parking.copy(wallMs = wallAt(31.0))
        val next = m.reduce(pullAway.state, MachineEvent.Finalised(ns(afterParkS + 3), end))
        assertEquals(RecorderState.CANDIDATE, next.state.phase)
        val created = next.effects.filterIsInstance<MachineEffect.CreateTrip>().single()
        assertEquals("trip-2", created.header.tripId)
        assertEquals(ns(afterParkS + 3), created.header.startMonoNs, "recording starts once the last trip is committed")
        assertEquals(wallAt(afterParkS + 3), created.header.startWallMs)
        assertEquals(end, created.header.parking, "the motion-start search stops at the parking just recorded")
        assertEquals(end, next.state.parking)
        assertTrue(MachineEffect.ArmParkedRegion(end.point, 120.0) in next.effects)
        assertNull(next.state.pendingWake)

        val confirmed = drive(next.state, (afterParkS + 4).toInt(), (afterParkS + 30).toInt(), 10.0, startNorth = 400.0)
        assertEquals(RecorderState.RECORDING, confirmed.state.phase)
        assertEquals("trip-2", confirmed.state.trip?.id, "two trips, not one merged trip")
    }

    @Test
    fun aDrivingFixAfterTheDeadlineEndsTheTripInsteadOfResumingIt() {
        val t = parked().fixes(fix(afterParkS, 12.0, 400.0))
        t.assertEndedAtTheStop()
        assertFalse(t.effects.any { it is MachineEffect.Append }, "the next drive's fixes are not part of the parked trip")

        val woke = wake(t.state, afterParkS + 2, WakeKind.ACTIVITY_VEHICLE, id = "trip-2")
        assertEquals(RecorderState.FINISHING, woke.state.phase)
        val next = m.reduce(woke.state, MachineEvent.Finalised(ns(afterParkS + 4), parking))
        assertEquals(RecorderState.CANDIDATE, next.state.phase)
        assertEquals("trip-2", next.state.trip?.id)
    }

    @Test
    fun aPullAwayJustBeforeTheDeadlineStillContinuesTheSameTrip() {
        val s = parked()
        val redLight = s.fixes(fix(270.5, 3.5, 310.0))
        assertEquals(RecorderState.RECORDING, redLight.state.phase)
        assertEquals(s.trip, redLight.state.trip)
        assertTrue(MachineEffect.Cancel(TimerId.STOP) in redLight.effects)
        assertTrue(redLight.effects.none { it is MachineEffect.Finalise })

        s.fixes(fix(271.0, 3.5, 310.0)).assertEndedAtTheStop()
    }

    @Test
    fun anOverdueTunnelResumeFromImuMotionEndsTheTripInstead() {
        val s = parked()
        val car = m.reduce(s, MachineEvent.Activity(ns(66.0), listOf(activity(66.0, ActivityType.AUTOMOTIVE)))).state
        val beforeDeadline = m.reduce(car, MachineEvent.Motion(ns(106.0), motionRun(100.0, 6.0, noisy = true)))
        assertEquals(RecorderState.RECORDING, beforeDeadline.state.phase, "the EC-TUN fallback still resumes in time")

        val late = m.reduce(car, MachineEvent.Motion(ns(afterParkS), motionRun(afterParkS - 6, 6.0, noisy = true)))
        late.assertEndedAtTheStop()
    }

    @Test
    fun oneDelayedBatchThatParksAndDrivesOnEndsTheTripAtTheStop() {
        val moving = drive(wake(idle, 0.0).state, 1, 30, 10.0).state
        val parkedFixes = (31..65).map { fix(it.toDouble(), 0.0, 300.0) }
        val batch = parkedFixes + fix(afterParkS, 12.0, 400.0)
        val t = m.reduce(moving, MachineEvent.Fixes(ns(afterParkS), batch))
        t.assertEndedAtTheStop()
    }

    @Test
    fun anyLateInputEndsAnOverdueTrip() {
        val s = parked()
        val late = listOf(
            MachineEvent.Timer(ns(afterParkS), TimerId.IMU_IDLE),
            MachineEvent.Foreground(ns(afterParkS)),
            MachineEvent.Power(ns(afterParkS), PowerStatus(80, isCharging = false, lowPowerMode = false, thermal = ThermalState.NOMINAL)),
            MachineEvent.Activity(ns(afterParkS), listOf(activity(afterParkS, ActivityType.STATIONARY))),
            MachineEvent.Motion(ns(afterParkS), motionRun(afterParkS - 2, 2.0, noisy = false)),
            MachineEvent.Timer(ns(afterParkS), TimerId.STOP),
            MachineEvent.UserStop(ns(afterParkS)),
        )
        for (event in late) {
            val t = m.reduce(s, event)
            t.assertEndedAtTheStop()
            assertEquals(1, t.effects.count { it is MachineEffect.Finalise }, "$event")
        }
        assertEquals(RecorderState.PAUSED, m.reduce(s, MachineEvent.Foreground(ns(270.0))).state.phase)
    }

    @Test
    fun aManualTripIsOverdueOnlyAfter900Seconds() {
        val manual = drive(wake(idle, 0.0, WakeKind.MANUAL).state, 1, 30, 10.0).state
        val s = drive(manual, 31, 65, 0.0, startNorth = 300.0).state
        assertEquals(RecorderState.RECORDING, s.fixes(fix(930.5, 4.0, 310.0)).state.phase)
        assertEquals(RecorderState.FINISHING, s.fixes(fix(931.0, 4.0, 310.0)).state.phase)
    }

    @Test
    fun thePassengerChoiceStillAppliesWithTheEndAtTheStop() {
        val s = parked()
        val kept = m.reduce(s, MachineEvent.Passenger(ns(afterParkS), keep = true))
        assertEquals(TripKind.PASSENGER, kept.finalise().kind)
        assertEquals(ns(31.0), kept.finalise().endNs)

        val dropped = m.reduce(s, MachineEvent.Passenger(ns(afterParkS), keep = false))
        assertEquals(RecorderState.IDLE, dropped.state.phase)
        assertTrue(dropped.effects.any { it is MachineEffect.Discard })
        assertTrue(dropped.effects.none { it is MachineEffect.Finalise })
    }

    @Test
    fun aWakeKeptWhileFinishingIsDroppedWhenStaleOrWhenAutomaticTripsAreOff() {
        val finishing = wake(parked(), afterParkS, WakeKind.ACTIVITY_VEHICLE, id = "trip-2").state

        val stale = m.reduce(finishing, MachineEvent.Finalised(ns(afterParkS + 301), parking))
        assertEquals(RecorderState.IDLE, stale.state.phase)
        assertTrue(MachineEffect.StopGnss in stale.effects)
        assertTrue(stale.effects.none { it is MachineEffect.CreateTrip })

        val off = m.reduce(finishing, MachineEvent.AutoDetect(ns(afterParkS + 1), enabled = false)).state
        val idleAgain = m.reduce(off, MachineEvent.Finalised(ns(afterParkS + 2), parking))
        assertEquals(RecorderState.IDLE, idleAgain.state.phase)
        assertTrue(idleAgain.effects.none { it is MachineEffect.CreateTrip })
    }

    @Test
    fun aLateStaleWakeDoesNotDisplaceTheWakeThatEndedTheTrip() {
        val finishing = wake(parked(), afterParkS, WakeKind.ACTIVITY_VEHICLE, id = "trip-2").state
        val staleAtS = afterParkS - 400
        val stale = m.reduce(
            finishing,
            MachineEvent.Wake(
                ns(afterParkS + 1), wallAt(afterParkS + 1), WakeEvent(WakeKind.PARKED_EXIT, ns(staleAtS), wallAt(staleAtS)),
                header(afterParkS + 1, WakeKind.PARKED_EXIT, id = "trip-3"),
            ),
        )
        assertEquals("trip-2", stale.state.pendingWake?.newTrip?.tripId)

        val manual = wake(stale.state, afterParkS + 2, WakeKind.MANUAL, id = "trip-4").state
        assertEquals("trip-4", manual.pendingWake?.newTrip?.tripId, "the user's Start replaces an automatic wake")
        val auto = wake(manual, afterParkS + 3, WakeKind.ACTIVITY_VEHICLE, id = "trip-5").state
        assertEquals("trip-4", auto.pendingWake?.newTrip?.tripId, "and is never replaced")
        val next = m.reduce(auto, MachineEvent.Finalised(ns(afterParkS + 4), parking))
        assertEquals(RecorderState.RECORDING, next.state.phase)
        assertEquals("trip-4", next.state.trip?.id)
    }

    @Test
    fun aWakeWhileAnyTripIsFinishingStartsTheNextTripOnceItIsCommitted() {
        val moving = drive(wake(idle, 0.0, WakeKind.MANUAL).state, 1, 30, 10.0).state
        val finishing = m.reduce(moving, MachineEvent.UserStop(ns(40.0))).state
        val woke = wake(finishing, 41.0, id = "trip-2")
        assertEquals(RecorderState.FINISHING, woke.state.phase)
        assertTrue(MachineEffect.CheckIn in woke.effects, "a wake is a check-in even when it starts nothing yet")
        val next = m.reduce(woke.state, MachineEvent.Finalised(ns(42.0), parking))
        assertEquals(RecorderState.CANDIDATE, next.state.phase)
        assertEquals(1, next.effects.count { it == MachineEffect.CheckIn })
        assertTrue(MachineEffect.StartGnss in next.effects)
    }
}
