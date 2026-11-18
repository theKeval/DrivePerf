package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.AutoDetectPauseLength
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.ThermalState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.InterruptionReason
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Table-driven checks of every row of pipeline-and-scoring.md §1.4, plus the Spike 1 capture changes. */
class TripMachineTest {
    private val m = TripMachine()
    private val idle = MachineState(parking = parking)

    private fun wake(atS: Double, kind: WakeKind = WakeKind.PARKED_EXIT, eventAtS: Double = atS, s: MachineState = idle) =
        m.reduce(s, MachineEvent.Wake(ns(atS), wallAt(atS), WakeEvent(kind, ns(eventAtS), wallAt(eventAtS)), header(atS, kind)))

    private fun MachineState.fixes(vararg f: com.driveperf.domain.capture.LocationSample, nowS: Double = f.last().tMonoNs / 1e9) =
        m.reduce(this, MachineEvent.Fixes(ns(nowS), f.toList()))

    /** Feeds one fix per second from [fromS] to [toS] at [speed], moving north. Returns the final transition. */
    private fun drive(s: MachineState, fromS: Int, toS: Int, speed: Double, startNorth: Double = 0.0): Transition {
        var t = Transition(s)
        val effects = mutableListOf<MachineEffect>()
        for (i in fromS..toS) {
            t = t.state.fixes(fix(i.toDouble(), speed, startNorth + (i - fromS) * speed))
            effects += t.effects
        }
        return Transition(t.state, effects)
    }

    private fun recording(): MachineState = wake(0.0, WakeKind.MANUAL).state

    /** An automatic trip, confirmed by speed at t = 21 s. */
    private fun autoRecording(): MachineState = drive(wake(0.0).state, 1, 21, 8.0).state

    private fun paused(start: MachineState = recording()): MachineState {
        val moving = drive(start, 1, 10, 10.0).state
        return drive(moving, 11, 45, 0.0, startNorth = 100.0).state
    }

    @Test
    fun autoWakeStartsACandidateRecordingFromTheWake() {
        val t = wake(1000.0)
        assertEquals(RecorderState.CANDIDATE, t.state.phase)
        assertTrue(t.effects.any { it is MachineEffect.CreateTrip && !it.manual })
        assertTrue(MachineEffect.StartGnss in t.effects)
        assertTrue(MachineEffect.StartImu(50) in t.effects)
        assertTrue(MachineEffect.Schedule(TimerId.CANDIDATE_TIMEOUT, ns(180.0)) in t.effects)
    }

    @Test
    fun activityHistoryIsBoundedByTheLastParking() {
        val recentParking = parking.copy(wallMs = wallAt(1000.0 - 120))
        val t = m.reduce(
            MachineState(parking = recentParking),
            MachineEvent.Wake(
                ns(1000.0), wallAt(1000.0), WakeEvent(WakeKind.PARKED_EXIT, ns(1000.0), wallAt(1000.0)),
                header(1000.0, parking = recentParking),
            ),
        )
        val query = t.effects.filterIsInstance<MachineEffect.QueryActivityHistory>().single()
        assertEquals(recentParking.wallMs, query.fromWallMs)
        val unbounded = wake(1000.0, s = MachineState()).effects.filterIsInstance<MachineEffect.QueryActivityHistory>().single()
        assertEquals(wallAt(1000.0) - 900_000, unbounded.fromWallMs)
    }

    @Test
    fun staleOrDisabledWakesAreIgnored() {
        assertEquals(RecorderState.IDLE, wake(1000.0, eventAtS = 1000.0 - 301).state.phase)
        assertEquals(RecorderState.CANDIDATE, wake(1000.0, eventAtS = 1000.0 - 299).state.phase)
        assertEquals(RecorderState.IDLE, wake(1000.0, s = idle.copy(autoDetect = false)).state.phase)
        assertEquals(RecorderState.IDLE, wake(1000.0, WakeKind.MANUAL, s = idle.copy(dbLocked = true)).state.phase)
        assertEquals(RecorderState.RECORDING, wake(1000.0, WakeKind.MANUAL, eventAtS = 0.0, s = idle.copy(autoDetect = false)).state.phase)
    }

    @Test
    fun manualStartRecordsAtOnceAndPromotesACandidate() {
        val manual = wake(0.0, WakeKind.MANUAL)
        assertEquals(RecorderState.RECORDING, manual.state.phase)
        assertTrue(manual.state.trip!!.manual)
        val promoted = wake(5.0, WakeKind.MANUAL, s = wake(0.0).state)
        assertEquals(RecorderState.RECORDING, promoted.state.phase)
        assertTrue(MachineEffect.Cancel(TimerId.CANDIDATE_TIMEOUT) in promoted.effects)
        assertTrue(promoted.effects.any { it is MachineEffect.ConfirmTrip && it.manual })
        assertEquals(listOf<MachineEffect>(MachineEffect.CheckIn), wake(10.0, s = manual.state).effects, "a wake mid-trip only checks in")
    }

    @Test
    fun speedHeldFor20SecondsConfirmsAndRunsTheBackfill() {
        val t = drive(wake(0.0).state, 1, 21, 8.0)
        assertEquals(RecorderState.RECORDING, t.state.phase)
        assertTrue(t.effects.any { it is MachineEffect.RunBackfill })
        assertTrue(t.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.CONFIRMED })
        assertEquals(RecorderState.CANDIDATE, drive(wake(0.0).state, 1, 20, 8.0).state.phase)
    }

    @Test
    fun aSlowerFixRestartsTheHold() {
        var s = drive(wake(0.0).state, 1, 15, 8.0).state
        s = s.fixes(fix(16.0, 5.0)).state
        s = drive(s, 17, 36, 8.0).state
        assertEquals(RecorderState.CANDIDATE, s.phase)
        assertEquals(RecorderState.RECORDING, s.fixes(fix(37.0, 8.0)).state.phase)
    }

    @Test
    fun automotiveActivityConfirmsAtTenKmh() {
        val withActivity = m.reduce(wake(0.0).state, MachineEvent.Activity(ns(1.0), listOf(activity(1.0, ActivityType.AUTOMOTIVE)))).state
        assertEquals(RecorderState.RECORDING, withActivity.fixes(fix(2.0, 3.0)).state.phase)
        val lowConfidence = m.reduce(
            wake(0.0).state,
            MachineEvent.Activity(ns(1.0), listOf(activity(1.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW))),
        ).state
        assertEquals(RecorderState.CANDIDATE, lowConfidence.fixes(fix(2.0, 3.0)).state.phase)
    }

    @Test
    fun anOldFixCannotConfirmThroughActivity() {
        val s = wake(0.0).state.fixes(fix(1.0, 3.0), nowS = 1.0).state
        val late = m.reduce(s, MachineEvent.Activity(ns(10.0), listOf(activity(10.0, ActivityType.AUTOMOTIVE))))
        assertEquals(RecorderState.CANDIDATE, late.state.phase)
        val fresh = m.reduce(s, MachineEvent.Activity(ns(4.0), listOf(activity(4.0, ActivityType.AUTOMOTIVE))))
        assertEquals(RecorderState.RECORDING, fresh.state.phase)
    }

    @Test
    fun noGoodFixMeansNoConfirmation() {
        var s = wake(0.0).state
        for (i in 1..30) s = s.fixes(fix(i.toDouble(), 10.0, i * 10.0, hAcc = 80.0)).state
        assertEquals(RecorderState.CANDIDATE, s.phase)
    }

    @Test
    fun cyclingBlocksTheSpeedRule() {
        val cycling = m.reduce(wake(0.0).state, MachineEvent.Activity(ns(0.5), listOf(activity(0.5, ActivityType.CYCLING)))).state
        assertEquals(RecorderState.CANDIDATE, drive(cycling, 1, 30, 8.0).state.phase)
    }

    @Test
    fun aWalkerIsRejectedAfter90Seconds() {
        val walking = m.reduce(wake(0.0).state, MachineEvent.Activity(ns(1.0), listOf(activity(1.0, ActivityType.WALKING)))).state
        assertEquals(RecorderState.CANDIDATE, walking.fixes(fix(89.0, 1.4)).state.phase)
        val t = walking.fixes(fix(90.0, 1.4))
        assertEquals(RecorderState.IDLE, t.state.phase)
        assertTrue(t.effects.any { it is MachineEffect.Discard })
        assertTrue(MachineEffect.StopGnss in t.effects)
        assertTrue(t.effects.any { it is MachineEffect.ArmParkedRegion })
    }

    @Test
    fun theCandidateTimesOutAfter180Seconds() {
        val s = wake(0.0).state
        assertEquals(RecorderState.CANDIDATE, m.reduce(s, MachineEvent.Timer(ns(100.0), TimerId.CANDIDATE_TIMEOUT)).state.phase)
        val t = m.reduce(s, MachineEvent.Timer(ns(180.0), TimerId.CANDIDATE_TIMEOUT))
        assertEquals(RecorderState.IDLE, t.state.phase)
        assertTrue(t.effects.any { it is MachineEffect.Discard })
    }

    @Test
    fun cachedAndOutOfOrderFixesAreDropped() {
        val s = wake(1000.0).state
        val t = s.fixes(fix(1000.0 - 600, 10.0), fix(1001.0, 0.0), fix(1000.5, 0.0), nowS = 1001.0)
        val appended = t.effects.filterIsInstance<MachineEffect.Append>().single().batch.fixes
        assertEquals(listOf(ns(1001.0)), appended.map { it.tMonoNs })
        assertTrue(s.fixes(fix(1000.0 - 20, 0.0), nowS = 1001.0).effects.isNotEmpty())
    }

    @Test
    fun liveDistanceCountsGatedStepsOfFiveMetresOrMore() {
        var s = recording()
        s = s.fixes(fix(1.0, 1.0, 0.0)).state
        s = s.fixes(fix(2.0, 1.0, 3.0)).state
        s = s.fixes(fix(3.0, 1.0, 10.0)).state
        s = s.fixes(fix(4.0, 1.0, 500.0, hAcc = 60.0)).state
        assertEquals(10.0, s.distanceM, 0.05)
    }

    @Test
    fun threeSlowFixesHeldFor30SecondsPause() {
        val moving = drive(recording(), 1, 10, 10.0).state
        val stopping = drive(moving, 11, 40, 0.2, startNorth = 100.0)
        assertEquals(RecorderState.RECORDING, stopping.state.phase)
        val t = stopping.state.fixes(fix(41.0, 0.2, 100.0))
        assertEquals(RecorderState.PAUSED, t.state.phase)
        assertEquals(ns(11.0), t.state.stoppingBeganAtNs)
        assertTrue(MachineEffect.Schedule(TimerId.STOP, ns(900.0 - 30)) in t.effects, "manual trips wait 900 s")
        assertTrue(MachineEffect.Schedule(TimerId.IMU_IDLE, ns(120.0)) in t.effects)
    }

    @Test
    fun aPlatformStationarySignalStartsTheStopAtTheFirstSlowFix() {
        val moving = drive(recording(), 1, 10, 10.0).state
        val still = m.reduce(moving, MachineEvent.Activity(ns(10.5), listOf(activity(10.5, ActivityType.STATIONARY)))).state
        val t = still.fixes(fix(11.0, 0.1, 100.0))
        assertEquals(ns(11.0), t.state.stoppingBeganAtNs)
    }

    @Test
    fun idlingDropsTheImuTo10HzAndAPullAwayRestores50Hz() {
        val s = paused()
        val idleT = m.reduce(s, MachineEvent.Timer(ns(170.0), TimerId.IMU_IDLE))
        assertTrue(MachineEffect.SetImuRate(10) in idleT.effects)
        val woke = m.reduce(idleT.state, MachineEvent.Motion(ns(171.0), listOf(CaptureFixtures.motion(171.0, accel = 0.05))))
        assertTrue(MachineEffect.SetImuRate(50) in woke.effects)
        assertEquals(50, woke.state.imuHz)
    }

    @Test
    fun lingeringAutomotiveAndAStillPhoneDoNotDelayParking() {
        val parked = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
        val car = m.reduce(parked, MachineEvent.Activity(ns(66.0), listOf(activity(66.0, ActivityType.AUTOMOTIVE)))).state
        val still = m.reduce(car, MachineEvent.Motion(ns(100.0), motionRun(66.0, 34.0, noisy = false)))
        assertEquals(RecorderState.PAUSED, still.state.phase)
        val timer = m.reduce(still.state, MachineEvent.Timer(ns(271.0), TimerId.STOP))
        assertEquals(ns(31.0), timer.effects.filterIsInstance<MachineEffect.Finalise>().single().endNs)
    }

    @Test
    fun walkingAwayWithLingeringAutomotiveDoesNotResume() {
        val parked = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
        val car = m.reduce(parked, MachineEvent.Activity(ns(66.0), listOf(activity(66.0, ActivityType.AUTOMOTIVE)))).state
        val motion = m.reduce(car, MachineEvent.Motion(ns(72.0), motionRun(66.0, 6.0, noisy = true)))
        val walked = drive(motion.state, 72, 130, 1.5, startNorth = 200.0)
        assertEquals(RecorderState.PAUSED, walked.state.phase)
        assertFalse(MachineEffect.Cancel(TimerId.STOP) in motion.effects + walked.effects)
        val timer = m.reduce(walked.state, MachineEvent.Timer(ns(271.0), TimerId.STOP))
        assertEquals(ns(31.0), timer.effects.filterIsInstance<MachineEffect.Finalise>().single().endNs)
    }

    @Test
    fun tunnelQueueThenDriveOnWithoutAFixKeepsOneTrip() {
        val queued = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
        val car = m.reduce(queued, MachineEvent.Activity(ns(66.0), listOf(activity(66.0, ActivityType.AUTOMOTIVE)))).state
        val still = m.reduce(car, MachineEvent.Motion(ns(200.0), motionRun(66.0, 134.0, noisy = false))).state
        assertEquals(RecorderState.PAUSED, still.phase)
        val moving = m.reduce(still, MachineEvent.Motion(ns(206.0), motionRun(200.0, 6.0, noisy = true)))
        assertEquals(RecorderState.RECORDING, moving.state.phase)
        assertEquals(queued.trip, moving.state.trip)
        assertTrue(MachineEffect.Cancel(TimerId.STOP) in moving.effects)
        assertEquals(RecorderState.RECORDING, m.reduce(moving.state, MachineEvent.Timer(ns(271.0), TimerId.STOP)).state.phase)
    }

    @Test
    fun freshFixesAndLingeringAutomotiveNeverResumeFromImuMotion() {
        var state = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
        state = m.reduce(state, MachineEvent.Activity(ns(66.0), listOf(activity(66.0, ActivityType.AUTOMOTIVE)))).state
        for (i in 66..270) {
            state = state.fixes(fix(i.toDouble(), 0.0, 200.0)).state
            val t = m.reduce(state, MachineEvent.Motion(ns(i + 1.0), motionRun(i.toDouble(), 1.0, noisy = true)))
            assertEquals(RecorderState.PAUSED, t.state.phase)
            assertFalse(MachineEffect.Cancel(TimerId.STOP) in t.effects)
            state = t.state
        }
        val timer = m.reduce(state, MachineEvent.Timer(ns(271.0), TimerId.STOP))
        assertEquals(ns(31.0), timer.effects.filterIsInstance<MachineEffect.Finalise>().single().endNs)
    }

    @Test
    fun aShortPhoneMovementAndAnOpenWindowDoNotSatisfyTheTunnelHold() {
        var state = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
            .copy(activity = activity(66.0, ActivityType.AUTOMOTIVE))
        for (i in 0..220) {
            val at = 100.0 + i * 0.02
            val accel = if (i <= 100 && i % 2 == 0) 0.05 else 0.0
            val t = m.reduce(state, MachineEvent.Motion(ns(at), listOf(CaptureFixtures.motion(at, accel = accel))))
            assertEquals(RecorderState.PAUSED, t.state.phase, "one closed non-still window plus unclassified time")
            assertFalse(MachineEffect.Cancel(TimerId.STOP) in t.effects)
            state = t.state
        }
        val timer = m.reduce(state, MachineEvent.Timer(ns(271.0), TimerId.STOP))
        assertEquals(ns(31.0), timer.effects.filterIsInstance<MachineEffect.Finalise>().single().endNs)
    }

    @Test
    fun tunnelFallbackRequiresStalenessSustainedMotionAndMediumAutomotive() {
        val queued = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
        fun noisy(s: MachineState, atS: Double) = m.reduce(s, MachineEvent.Motion(ns(atS), motionRun(atS - 6.0, 6.0, noisy = true)))
        for (a in listOf(null, activity(66.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW), activity(66.0, ActivityType.WALKING))) {
            assertEquals(RecorderState.PAUSED, noisy(queued.copy(activity = a), 100.0).state.phase)
        }
        val car = queued.copy(activity = activity(66.0, ActivityType.AUTOMOTIVE))
        assertEquals(RecorderState.PAUSED, noisy(car, 94.9).state.phase)
        assertEquals(RecorderState.RECORDING, noisy(car, 95.0).state.phase)
        val short = m.reduce(car, MachineEvent.Motion(ns(100.0), motionRun(98.0, 2.0, noisy = true)))
        assertEquals(RecorderState.PAUSED, short.state.phase)
        val gap = m.reduce(short.state, MachineEvent.Motion(ns(200.0), listOf(CaptureFixtures.motion(200.0, accel = 0.1))))
        assertEquals(RecorderState.PAUSED, gap.state.phase)
        val delayed = m.reduce(car, MachineEvent.Motion(ns(200.0), motionRun(100.0, 6.0, noisy = true)))
        assertEquals(RecorderState.PAUSED, delayed.state.phase)
        val still = m.reduce(car, MachineEvent.Motion(ns(100.0), motionRun(94.0, 6.0, noisy = false)))
        assertEquals(RecorderState.PAUSED, still.state.phase)
    }

    @Test
    fun aFreshAccuratePullAwayBeforeTheTimerContinuesTheSameTrip() {
        val stopped = drive(autoRecording(), 31, 65, 0.0, startNorth = 200.0).state
        val old = stopped.fixes(fix(77.0, 4.0, 210.0), nowS = 85.0)
        val inaccurate = old.state.fixes(fix(89.0, 4.0, 220.0, hAcc = 60.0))
        assertEquals(RecorderState.PAUSED, inaccurate.state.phase)
        val t = inaccurate.state.fixes(fix(270.0, 3.0, 205.0))
        assertEquals(RecorderState.RECORDING, t.state.phase)
        assertEquals(stopped.trip, t.state.trip)
        assertTrue(MachineEffect.Cancel(TimerId.STOP) in t.effects)
        assertEquals(RecorderState.RECORDING, m.reduce(t.state, MachineEvent.Timer(ns(271.0), TimerId.STOP)).state.phase)

        val parkedAgain = drive(t.state, 300, 335, 0.0, startNorth = 500.0).state
        val timer = m.reduce(parkedAgain, MachineEvent.Timer(ns(540.0), TimerId.STOP))
        assertEquals(ns(300.0), timer.effects.filterIsInstance<MachineEffect.Finalise>().single().endNs)
    }

    @Test
    fun aKnownHumanPoweredActivityBlocksTheResume() {
        val s = paused()
        val walking = m.reduce(s, MachineEvent.Activity(ns(45.5), listOf(activity(45.5, ActivityType.WALKING)))).state
        assertEquals(RecorderState.PAUSED, drive(walking, 46, 48, 3.0, startNorth = 100.0).state.phase)
    }

    @Test
    fun theStopTimerEndsTheTripAtTheMomentStoppingBegan() {
        val auto = drive(autoRecording(), 22, 30, 10.0, startNorth = 300.0).state
        val s = drive(auto, 31, 65, 0.0, startNorth = 500.0).state
        assertEquals(RecorderState.PAUSED, s.phase)
        assertEquals(RecorderState.PAUSED, m.reduce(s, MachineEvent.Timer(ns(100.0), TimerId.STOP)).state.phase)
        val t = m.reduce(s, MachineEvent.Timer(ns(31.0 + 240), TimerId.STOP))
        assertEquals(RecorderState.FINISHING, t.state.phase)
        val finalise = t.effects.filterIsInstance<MachineEffect.Finalise>().single()
        assertEquals(ns(31.0), finalise.endNs)
        assertEquals(EndReason.STOP_TIMER, finalise.reason)
        assertFalse(MachineEffect.StopGnss in t.effects, "GNSS stays on until the commit")
    }

    @Test
    fun aManualTripWaits900Seconds() {
        val s = paused()
        assertEquals(RecorderState.PAUSED, m.reduce(s, MachineEvent.Timer(ns(11.0 + 240), TimerId.STOP)).state.phase)
        assertEquals(RecorderState.FINISHING, m.reduce(s, MachineEvent.Timer(ns(11.0 + 900), TimerId.STOP)).state.phase)
    }

    @Test
    fun userStopAndPassengerChoices() {
        val s = recording()
        val stopped = m.reduce(s, MachineEvent.UserStop(ns(50.0)))
        assertEquals(ns(50.0), stopped.effects.filterIsInstance<MachineEffect.Finalise>().single().endNs)
        val kept = m.reduce(s, MachineEvent.Passenger(ns(50.0), keep = true))
        assertEquals(TripKind.PASSENGER, kept.effects.filterIsInstance<MachineEffect.Finalise>().single().kind)
        val dropped = m.reduce(s.fixes(fix(40.0, 5.0)).state, MachineEvent.Passenger(ns(50.0), keep = false))
        assertEquals(RecorderState.IDLE, dropped.state.phase)
        assertTrue(dropped.effects.any { it is MachineEffect.Discard })
        assertTrue(dropped.effects.any { it is MachineEffect.ArmParkedRegion })
        assertTrue(m.reduce(idle, MachineEvent.UserStop(ns(1.0))).effects.isEmpty())
        assertTrue(m.reduce(idle, MachineEvent.Passenger(ns(1.0), keep = true)).effects.isEmpty())
    }

    @Test
    fun powerChangesSetTheImuRateAndAFlatBatteryEndsTheTrip() {
        val s = recording()
        val lpm = m.reduce(s, MachineEvent.Power(ns(5.0), PowerStatus(60, false, true, ThermalState.NOMINAL)))
        assertTrue(MachineEffect.SetImuRate(25) in lpm.effects)
        val hot = m.reduce(s, MachineEvent.Power(ns(5.0), PowerStatus(60, false, false, ThermalState.CRITICAL)))
        assertTrue(MachineEffect.SetImuRate(10) in hot.effects)
        assertTrue(m.reduce(s, MachineEvent.Power(ns(5.0), PowerStatus(60, false, false, ThermalState.NOMINAL))).effects.isEmpty())
        val flat = m.reduce(s, MachineEvent.Power(ns(5.0), PowerStatus(5, false, false, ThermalState.NOMINAL)))
        assertEquals(InterruptionReason.LOW_BATTERY, flat.effects.filterIsInstance<MachineEffect.Finalise>().single().interruption)
        val charging = m.reduce(s, MachineEvent.Power(ns(5.0), PowerStatus(5, true, false, ThermalState.NOMINAL)))
        assertEquals(RecorderState.RECORDING, charging.state.phase)
        val idleFlat = m.reduce(idle, MachineEvent.Power(ns(1.0), PowerStatus(5, false, false, ThermalState.NOMINAL)))
        assertEquals(RecorderState.IDLE, idleFlat.state.phase)
    }

    @Test
    fun batterySaverAppliesNowAndSurvivesTheNextTrip() {
        val on = m.reduce(recording(), MachineEvent.BatterySaver(ns(1.0), true))
        assertEquals(25, on.state.imuHz)
        assertTrue(MachineEffect.SetImuRate(25) in on.effects)
        val off = m.reduce(on.state, MachineEvent.BatterySaver(ns(2.0), false))
        assertEquals(50, off.state.imuHz)
        val armed = m.reduce(idle, MachineEvent.BatterySaver(0, true)).state
        val started = wake(1.0, WakeKind.MANUAL, s = armed)
        assertEquals(25, started.state.imuHz)
        assertTrue(started.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.IMU_RATE && it.detail == "25" })
        val recovery = Recovery(header(0.0), manual = true, confirmed = true, lastSampleWallMs = wallAt(1.0), wasMoving = true)
        val resumed = m.reduce(armed, MachineEvent.Launch(ns(2.0), wallAt(2.0), true, parking, recovery))
        assertEquals(25, resumed.state.imuHz)
        assertTrue(resumed.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.IMU_RATE && it.detail == "25" })
        assertEquals(25, m.rateFor(null, true))
        assertEquals(10, m.rateFor(PowerStatus(50, false, false, ThermalState.CRITICAL), true))
    }

    @Test
    fun rateFollowsSection27() {
        assertEquals(50, m.rateFor(null))
        assertEquals(25, m.rateFor(PowerStatus(15, false, false, ThermalState.NOMINAL)))
        assertEquals(25, m.rateFor(PowerStatus(null, false, false, ThermalState.SERIOUS)))
        assertEquals(50, m.rateFor(PowerStatus(16, false, false, ThermalState.FAIR)))
    }

    @Test
    fun lowFreeSpaceStopsTheImuButKeepsGnss() {
        val s = recording()
        val t = m.reduce(s, MachineEvent.FreeSpace(ns(5.0), 10L))
        assertTrue(MachineEffect.StopImu in t.effects)
        assertTrue(t.state.imuStoppedForSpace)
        val after = m.reduce(t.state, MachineEvent.Motion(ns(6.0), motionRun(5.5, 1.0, noisy = true)))
        assertTrue(after.effects.none { it is MachineEffect.Append })
        assertTrue(m.reduce(t.state, MachineEvent.FreeSpace(ns(7.0), 0L)).effects.isEmpty())
        assertTrue(m.reduce(s, MachineEvent.FreeSpace(ns(5.0), Long.MAX_VALUE)).effects.isEmpty())
    }

    @Test
    fun finalisingReturnsToIdleAndRearmsTheParkedRegionAtTheEnd() {
        val finishing = m.reduce(recording(), MachineEvent.UserStop(ns(50.0))).state
        val end = parking.copy(wallMs = wallAt(50.0))
        val t = m.reduce(finishing, MachineEvent.Finalised(ns(51.0), end))
        assertEquals(RecorderState.IDLE, t.state.phase)
        assertEquals(end, t.state.parking)
        assertTrue(MachineEffect.StopGnss in t.effects)
        assertTrue(MachineEffect.ArmParkedRegion(end.point, 120.0) in t.effects)
        assertTrue(m.reduce(idle, MachineEvent.Finalised(ns(1.0), end)).effects.isEmpty())
    }

    @Test
    fun launchArmsTheParkedRegion() {
        val t = m.reduce(MachineState(), MachineEvent.Launch(ns(1.0), wallAt(1.0), true, parking, null))
        assertTrue(MachineEffect.ArmParkedRegion(parking.point, 120.0) in t.effects)
        val locked = m.reduce(MachineState(), MachineEvent.Launch(ns(1.0), wallAt(1.0), false, null, null))
        assertTrue(locked.state.dbLocked)
        assertTrue(m.reduce(recording(), MachineEvent.Launch(ns(1.0), wallAt(1.0), true, null, null)).effects.isEmpty())
    }

    @Test
    fun aRecentKillResumesTheSameTripWithAGapMark() {
        val rec = Recovery(header(0.0), manual = false, confirmed = true, lastSampleWallMs = wallAt(100.0), wasMoving = true)
        val t = m.reduce(MachineState(), MachineEvent.Launch(ns(400.0), wallAt(400.0), true, parking, rec))
        assertEquals(RecorderState.RECORDING, t.state.phase)
        assertTrue(t.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.APP_KILLED && it.detail == "300" })
        val candidate = m.reduce(MachineState(), MachineEvent.Launch(ns(400.0), wallAt(400.0), true, parking, rec.copy(confirmed = false)))
        assertEquals(RecorderState.CANDIDATE, candidate.state.phase)
    }

    @Test
    fun anOldOrStationaryKillIsFinalisedAsInterrupted() {
        val rec = Recovery(header(0.0), manual = false, confirmed = true, lastSampleWallMs = wallAt(100.0), wasMoving = true)
        val old = m.reduce(MachineState(), MachineEvent.Launch(ns(800.0), wallAt(800.0), true, parking, rec))
        assertEquals(RecorderState.FINISHING, old.state.phase)
        val finalise = old.effects.filterIsInstance<MachineEffect.Finalise>().single()
        assertNull(finalise.endNs)
        assertEquals(InterruptionReason.APP_KILLED, finalise.interruption)
        val parked = m.reduce(MachineState(), MachineEvent.Launch(ns(200.0), wallAt(200.0), true, parking, rec.copy(wasMoving = false)))
        assertEquals(RecorderState.FINISHING, parked.state.phase)
    }

    @Test
    fun anUnconfirmedCandidateKilledAndNotResumedIsDiscardedNotKept() {
        val rec = Recovery(header(0.0), manual = false, confirmed = false, lastSampleWallMs = wallAt(50.0), wasMoving = false)
        val t = m.reduce(MachineState(), MachineEvent.Launch(ns(200.0), wallAt(200.0), true, parking, rec))
        assertEquals(RecorderState.IDLE, t.state.phase)
        assertTrue(MachineEffect.Discard(rec.header.tripId) in t.effects)
        assertTrue(t.effects.none { it is MachineEffect.Finalise })
        assertTrue(MachineEffect.ArmParkedRegion(parking.point, 120.0) in t.effects)
    }

    @Test
    fun withoutGnssImuStillnessPausesAndAutomotiveMotionResumes() {
        val s = drive(recording(), 1, 5, 10.0).state
        val still = m.reduce(s, MachineEvent.Motion(ns(40.0), motionRun(6.0, 34.0, noisy = false)))
        assertEquals(RecorderState.PAUSED, still.state.phase)
        val car = m.reduce(still.state, MachineEvent.Activity(ns(40.5), listOf(activity(40.5, ActivityType.AUTOMOTIVE)))).state
        val moving = m.reduce(car, MachineEvent.Motion(ns(47.0), motionRun(41.0, 6.0, noisy = true)))
        assertEquals(RecorderState.RECORDING, moving.state.phase)
        assertEquals(RecorderState.RECORDING, moving.state.fixes(fix(48.0, 3.0)).state.phase)
    }

    @Test
    fun autoDetectToggles() {
        assertFalse(m.reduce(idle, MachineEvent.AutoDetect(ns(1.0), false)).state.autoDetect)
    }

    @Test
    fun eventsWhileIdleDoNothing() {
        assertTrue(idle.fixes(fix(1.0, 10.0)).effects.isEmpty())
        assertTrue(m.reduce(idle, MachineEvent.Motion(ns(1.0), motionRun(0.0, 1.0, true))).effects.isEmpty())
        assertTrue(m.reduce(idle, MachineEvent.ActivityHistory(ns(1.0), emptyList())).effects.isEmpty())
        val walking = m.reduce(idle, MachineEvent.Activity(ns(1.0), listOf(activity(1.0, ActivityType.WALKING))))
        assertEquals(ActivityType.WALKING, walking.state.activity?.type)
        assertTrue(m.reduce(idle, MachineEvent.Timer(ns(1.0), TimerId.STOP)).effects.isEmpty())
    }

    @Test
    fun switchingAutomaticTripsOffAlwaysRemovesTheResumeNotice() {
        // A pause that has just ended on time: its notice may still be pending (Android's 10-minute alarm window).
        val paused = m.reduce(
            idle,
            MachineEvent.PauseAutoDetect(ns(0.0), wallAt(0.0), AutoDetectPauseLength.ONE_HOUR, wallAt(3_600.0)),
        ).state
        val ended = m.reduce(paused, MachineEvent.PauseCheck(ns(3_601.0), wallAt(3_601.0)))
        assertNull(ended.state.pause)
        assertFalse(MachineEffect.CancelResumeNotice in ended.effects, "the notice that says it is back on stays")
        val off = m.reduce(ended.state, MachineEvent.AutoDetect(ns(3_660.0), false))
        assertTrue(MachineEffect.CancelResumeNotice in off.effects)
        assertTrue(MachineEffect.WatchdogOff in off.effects)
        assertFalse(off.effects.any { it is MachineEffect.SavePause }, "nothing paused to clear")

        val stillPaused = m.reduce(paused, MachineEvent.AutoDetect(ns(60.0), false))
        assertNull(stillPaused.state.pause)
        assertTrue(MachineEffect.CancelResumeNotice in stillPaused.effects)
        assertTrue(MachineEffect.SavePause(null) in stillPaused.effects)
        assertTrue(MachineEffect.Cancel(TimerId.PAUSE_END) in stillPaused.effects)
    }
}
