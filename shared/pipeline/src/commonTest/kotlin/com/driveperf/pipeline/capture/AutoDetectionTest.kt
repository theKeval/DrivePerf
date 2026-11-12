package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.capture.CaptureFixtures.activity
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.ns
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.pipeline.capture.CaptureFixtures.wallAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-CAP-02, F-CAP-03 and F-CAP-14 rules the reducer adds on top of §1.4: check-ins, analytics and the storage end. */
class AutoDetectionTest {
    private val m = TripMachine()
    private val idle = MachineState(parking = parking)

    private fun wake(atS: Double, kind: WakeKind = WakeKind.PARKED_EXIT, s: MachineState = idle, detail: String? = null) =
        m.reduce(s, MachineEvent.Wake(ns(atS), wallAt(atS), WakeEvent(kind, ns(atS), wallAt(atS), detail), header(atS, kind)))

    private fun drive(s: MachineState, fromS: Int, toS: Int, speed: Double): Transition {
        var t = Transition(s)
        val effects = mutableListOf<MachineEffect>()
        for (i in fromS..toS) {
            t = m.reduce(t.state, MachineEvent.Fixes(ns(i.toDouble()), listOf(fix(i.toDouble(), speed, i * speed))))
            effects += t.effects
        }
        return Transition(t.state, effects)
    }

    private fun Transition.tracked(): List<CaptureEvent> = effects.filterIsInstance<MachineEffect.Track>().map { it.event }

    private fun launch(s: MachineState = MachineState(), reason: RearmReason = RearmReason.LAUNCH) =
        m.reduce(s, MachineEvent.Launch(ns(1.0), wallAt(1.0), true, parking, null, reason))

    // ---- F-CAP-14 check-ins ----

    @Test
    fun launchRearmsChecksInAndSaysWhy() {
        val t = launch(reason = RearmReason.BOOT)
        assertTrue(t.effects.any { it is MachineEffect.ArmParkedRegion })
        assertTrue(MachineEffect.CheckIn in t.effects)
        assertEquals(listOf(CaptureEvent.rearmed(RearmReason.BOOT)), t.tracked())
        val off = launch(MachineState(autoDetect = false), RearmReason.FORCE_STOP)
        assertFalse(MachineEffect.CheckIn in off.effects, "no watchdog while auto-detect is off")
        assertEquals(listOf(CaptureEvent("rearmed", mapOf("reason" to "force_stop"))), off.tracked())
    }

    @Test
    fun everyWakeChecksInEvenWhenItStartsNothing() {
        assertTrue(MachineEffect.CheckIn in wake(0.0).effects)
        val oldEvent = WakeEvent(WakeKind.MOVE_SIGNAL, ns(0.0), wallAt(0.0))
        val stale = m.reduce(idle, MachineEvent.Wake(ns(1000.0), wallAt(1000.0), oldEvent, header(1000.0)))
        assertEquals(RecorderState.IDLE, stale.state.phase)
        assertEquals(listOf<MachineEffect>(MachineEffect.CheckIn), stale.effects)
        assertFalse(MachineEffect.CheckIn in wake(0.0, s = idle.copy(autoDetect = false)).effects)
    }

    @Test
    fun foregroundChecksInAndRearmsOnlyWhenIdle() {
        val t = m.reduce(idle, MachineEvent.Foreground(ns(1.0)))
        assertEquals(listOf(MachineEffect.ArmParkedRegion(parking.point, CaptureConfig().parkedRadiusM), MachineEffect.CheckIn), t.effects)
        val recording = wake(0.0, WakeKind.MANUAL).state
        assertEquals(listOf<MachineEffect>(MachineEffect.CheckIn), m.reduce(recording, MachineEvent.Foreground(ns(5.0))).effects)
    }

    @Test
    fun pausingAutoDetectRemovesTheWatchdogAndResumingChecksIn() {
        val off = m.reduce(idle, MachineEvent.AutoDetect(ns(1.0), false))
        assertEquals(listOf<MachineEffect>(MachineEffect.WatchdogOff), off.effects)
        assertTrue(m.reduce(off.state, MachineEvent.AutoDetect(ns(2.0), false)).effects.isEmpty(), "no change, nothing to do")
        val on = m.reduce(off.state, MachineEvent.AutoDetect(ns(3.0), true))
        assertTrue(MachineEffect.CheckIn in on.effects)
        assertTrue(on.effects.any { it is MachineEffect.ArmParkedRegion })
    }

    @Test
    fun theTripEndChecksInAgain() {
        val finishing = m.reduce(wake(0.0, WakeKind.MANUAL).state, MachineEvent.UserStop(ns(60.0))).state
        val done = m.reduce(finishing, MachineEvent.Finalised(ns(61.0), parking))
        assertTrue(MachineEffect.CheckIn in done.effects)
    }

    @Test
    fun triggerAvailabilitySurvivesTrips() {
        val blocked = m.reduce(idle, MachineEvent.TriggersAvailable(ns(1.0), false)).state
        assertFalse(blocked.triggersAvailable)
        val finishing = m.reduce(wake(2.0, WakeKind.MANUAL, s = blocked).state, MachineEvent.UserStop(ns(60.0))).state
        assertFalse(m.reduce(finishing, MachineEvent.Finalised(ns(61.0), parking)).state.triggersAvailable)
        assertEquals(listOf(CaptureEvent.watchdogOpened), m.reduce(idle, MachineEvent.WatchdogOpened(ns(1.0))).tracked())
    }

    // ---- F-CAP-02 analytics ----

    @Test
    fun aConfirmedCandidateReportsTheWakeTheRuleAndTheStart() {
        val woken = wake(0.0)
        assertEquals(listOf(CaptureEvent("auto_wake", mapOf("trigger" to "parked_exit"))), woken.tracked())
        val bySpeed = drive(woken.state, 1, 21, 8.0)
        assertEquals(
            listOf(
                CaptureEvent("auto_candidate", mapOf("result" to "confirmed", "reason" to "speed")),
                CaptureEvent("trip_started", mapOf("mode" to "auto", "trigger" to "parked_exit")),
            ),
            bySpeed.tracked(),
        )
        val automotive = m.reduce(wake(0.0).state, MachineEvent.Activity(ns(1.0), listOf(activity(1.0, ActivityType.AUTOMOTIVE))))
        val byActivity = m.reduce(automotive.state, MachineEvent.Fixes(ns(2.0), listOf(fix(2.0, 3.0))))
        assertEquals(CaptureEvent.confirmed(byActivity = true), byActivity.tracked().first())
    }

    @Test
    fun rejectionsSayWhy() {
        val running = m.reduce(wake(0.0).state, MachineEvent.Activity(ns(1.0), listOf(activity(1.0, ActivityType.RUNNING))))
        val rejected = m.reduce(running.state, MachineEvent.Fixes(ns(90.0), listOf(fix(90.0, 2.0))))
        assertEquals(listOf(CaptureEvent("auto_candidate", mapOf("result" to "rejected", "reason" to "running"))), rejected.tracked())
        val timedOut = m.reduce(wake(0.0).state, MachineEvent.Timer(ns(180.0), TimerId.CANDIDATE_TIMEOUT))
        assertEquals(listOf(CaptureEvent.rejected(RejectReason.TIMEOUT)), timedOut.tracked())
        assertEquals("speed", RejectReason.TIMEOUT.key, "the PRD's name for a candidate that never reached driving speed")
    }

    @Test
    fun manualStartsNameTheirSurface() {
        assertEquals(
            listOf(CaptureEvent("trip_started", mapOf("mode" to "manual", "trigger" to "tile"))),
            wake(0.0, WakeKind.MANUAL, detail = "qs_tile").tracked(),
        )
        val promoted = wake(5.0, WakeKind.MANUAL, s = wake(0.0).state, detail = "control_center")
        assertEquals(listOf(CaptureEvent.manualStarted("control_center")), promoted.tracked())
        assertEquals("control", promoted.tracked().single().properties["trigger"])
        assertEquals("in_app", CaptureEvent.manualStarted("app").properties["trigger"])
    }

    // ---- F-CAP-03 ends ----

    @Test
    fun tripEndedSaysWhatEndedIt() {
        val recording = drive(wake(0.0, WakeKind.MANUAL).state, 1, 10, 10.0).state
        assertEquals(listOf(CaptureEvent.ended("user")), m.reduce(recording, MachineEvent.UserStop(ns(20.0))).tracked())
        val paused = drive(recording, 11, 45, 0.0).state
        assertEquals(RecorderState.PAUSED, paused.phase)
        val stopped = m.reduce(paused, MachineEvent.Timer(ns(1000.0), TimerId.STOP))
        assertEquals(listOf(CaptureEvent("trip_ended", mapOf("by" to "stop_timer"))), stopped.tracked())
    }

    @Test
    fun aWriteThatStillFailsAfterTheImuStoppedEndsTheTripWithWhatItHas() {
        val recording = drive(wake(0.0, WakeKind.MANUAL).state, 1, 10, 10.0).state
        val id = recording.trip!!.id
        val first = m.reduce(recording, MachineEvent.AppendFailed(ns(11.0), id))
        assertEquals(RecorderState.RECORDING, first.state.phase)
        assertTrue(MachineEffect.StopImu in first.effects)
        val second = m.reduce(first.state, MachineEvent.AppendFailed(ns(12.0), id))
        assertEquals(RecorderState.FINISHING, second.state.phase)
        assertEquals(EndReason.STORAGE, second.effects.filterIsInstance<MachineEffect.Finalise>().single().reason)
        assertEquals(listOf(CaptureEvent.ended("storage")), second.tracked())
        assertTrue(m.reduce(recording, MachineEvent.AppendFailed(ns(11.0), "other")).effects.isEmpty())
    }

    @Test
    fun aCandidateThatCannotBeWrittenIsDropped() {
        val candidate = wake(0.0).state
        val id = candidate.trip!!.id
        val first = m.reduce(candidate, MachineEvent.AppendFailed(ns(1.0), id)).state
        val second = m.reduce(first, MachineEvent.AppendFailed(ns(2.0), id))
        assertEquals(RecorderState.IDLE, second.state.phase)
        assertEquals(listOf(CaptureEvent.rejected(RejectReason.STORAGE)), second.tracked())
    }
}
