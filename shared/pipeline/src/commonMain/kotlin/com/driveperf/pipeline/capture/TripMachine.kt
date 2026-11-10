package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RecorderState.CANDIDATE
import com.driveperf.domain.capture.RecorderState.FINISHING
import com.driveperf.domain.capture.RecorderState.IDLE
import com.driveperf.domain.capture.RecorderState.PAUSED
import com.driveperf.domain.capture.RecorderState.RECORDING
import com.driveperf.domain.capture.ThermalState
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.TripKind
import com.driveperf.pipeline.capture.MachineEffect.Append
import com.driveperf.pipeline.capture.MachineEffect.Cancel
import com.driveperf.pipeline.capture.MachineEffect.Mark
import com.driveperf.pipeline.capture.MachineEffect.Schedule
import com.driveperf.pipeline.geo.Geo

/**
 * The trip state machine (pipeline-and-scoring.md §1): one pure reducer, `(state, event) → (state, effects)`. It reads
 * no clock and does no I/O; [TripRecorderEngine] runs the effects.
 *
 * Spike 1 changes applied here: recording starts at the wake and the candidate's samples are kept (change 2); stale
 * wakes and cached fixes are dropped (change 8); the activity-history query is bounded by the last parking time
 * (change 3); the parked region is re-armed at every trip end and launch (change 1).
 */
@Suppress("TooManyFunctions") // one small function per transition-table row keeps each rule readable
internal class TripMachine(private val cfg: CaptureConfig = CaptureConfig()) {
    fun reduce(s: MachineState, e: MachineEvent): Transition = when (e) {
        is MachineEvent.Fixes -> onFixes(s, e)
        is MachineEvent.Motion -> onMotion(s, e)
        is MachineEvent.Activity -> onActivity(s, e.samples, e.nowNs)
        is MachineEvent.ActivityHistory -> onActivity(s, e.samples, e.nowNs)
        is MachineEvent.Power -> onPower(s, e)
        is MachineEvent.FreeSpace -> onFreeSpace(s, e)
        else -> reduceControl(s, e)
    }

    private fun reduceControl(s: MachineState, e: MachineEvent): Transition = when (e) {
        is MachineEvent.Launch -> onLaunch(s, e)
        is MachineEvent.Wake -> onWake(s, e)
        is MachineEvent.Timer -> onTimer(s, e)
        is MachineEvent.UserStop -> if (s.isLive()) finish(s, e.nowNs, EndReason.USER_STOP) else Transition(s)
        is MachineEvent.Passenger -> onPassenger(s, e)
        is MachineEvent.AutoDetect -> Transition(s.copy(autoDetect = e.enabled))
        is MachineEvent.Finalised -> onFinalised(s, e)
        else -> Transition(s)
    }

    // ---- launch and recovery (§1.7) ----

    private fun onLaunch(s: MachineState, e: MachineEvent.Launch): Transition {
        val base = s.copy(dbLocked = !e.dbAvailable, parking = e.parking ?: s.parking)
        val arm = armAt(base.parking)
        val rec = e.recovering
        return when {
            s.phase != IDLE -> Transition(s)
            !e.dbAvailable || rec == null -> Transition(base, arm)
            canResume(rec, e) -> resumeAfterKill(base, rec, e, arm)
            else -> {
                val trip = ActiveTrip(rec.header, rec.manual, rec.confirmed)
                val end = MachineEffect.Finalise(
                    trip.id, null, trip.kind, EndReason.RECOVERED_INTERRUPTED, InterruptionReason.APP_KILLED,
                )
                Transition(base.copy(phase = FINISHING, trip = trip), arm + MachineEffect.StartGnss + end)
            }
        }
    }

    private fun canResume(rec: Recovery, e: MachineEvent.Launch): Boolean =
        (e.nowWallMs - rec.lastSampleWallMs) / MS_PER_S <= cfg.resumeAfterKillS &&
            rec.wasMoving && e.nowNs >= rec.header.startMonoNs

    private fun resumeAfterKill(base: MachineState, rec: Recovery, e: MachineEvent.Launch, arm: List<MachineEffect>): Transition {
        val ageS = ((e.nowWallMs - rec.lastSampleWallMs) / MS_PER_S).toLong()
        val trip = ActiveTrip(rec.header, rec.manual, rec.confirmed)
        val hz = rateFor(base.power)
        val resumed = base.copy(phase = if (rec.confirmed) RECORDING else CANDIDATE, trip = trip, imuHz = hz)
        val fx = arm + sensorsOn(hz) + Mark(trip.id, e.nowNs, CaptureMarkType.APP_KILLED, ageS.toString())
        val timeout = if (rec.confirmed) emptyList() else listOf(Schedule(TimerId.CANDIDATE_TIMEOUT, secondsNs(cfg.candidateTimeoutS)))
        return Transition(resumed, fx + timeout)
    }

    private fun armAt(parking: Parking?): List<MachineEffect> =
        parking?.let { listOf(MachineEffect.ArmParkedRegion(it.point, cfg.parkedRadiusM)) }.orEmpty()

    // ---- wakes (§1.3, §1.4) ----

    private fun onWake(s: MachineState, e: MachineEvent.Wake): Transition {
        val manual = e.wake.kind == WakeKind.MANUAL
        return when {
            manual && s.phase == CANDIDATE -> promoteToManual(s, e.nowNs)
            s.phase != IDLE || s.dbLocked -> Transition(s)
            manual -> startCapture(s, e, manual = true)
            !s.autoDetect || isStale(e) -> Transition(s)
            else -> startCapture(s, e, manual = false)
        }
    }

    private fun isStale(e: MachineEvent.Wake): Boolean = e.nowNs - e.wake.tMonoNs > secondsNs(cfg.staleWakeS)

    private fun startCapture(s: MachineState, e: MachineEvent.Wake, manual: Boolean): Transition {
        val header = e.newTrip
        val trip = ActiveTrip(header, manual = manual, confirmed = manual)
        val hz = rateFor(s.power)
        val next = MachineState(
            phase = if (manual) RECORDING else CANDIDATE,
            autoDetect = s.autoDetect,
            parking = s.parking,
            trip = trip,
            activity = s.activity,
            power = s.power,
            imuHz = hz,
        )
        val historyFrom = historyStart(header)
        val fx = buildList {
            add(MachineEffect.CreateTrip(header, manual))
            addAll(sensorsOn(hz))
            add(Mark(trip.id, header.startMonoNs, CaptureMarkType.RECORDING_STARTED, e.wake.kind.name))
            add(MachineEffect.QueryActivityHistory(historyFrom, e.nowWallMs))
            if (!manual) add(Schedule(TimerId.CANDIDATE_TIMEOUT, secondsNs(cfg.candidateTimeoutS)))
        }
        return Transition(next, fx)
    }

    /** The motion-start search never reaches before the last parking (Spike 1 change 3), nor 15 min (P-14). */
    private fun historyStart(header: CaptureHeader): Long {
        val window = header.startWallMs - (cfg.activityHistoryS * MS_PER_S).toLong()
        return maxOf(window, header.parking?.wallMs ?: window)
    }

    private fun promoteToManual(s: MachineState, nowNs: Long): Transition {
        val trip = s.trip ?: return Transition(s)
        val next = s.copy(phase = RECORDING, trip = trip.copy(manual = true, confirmed = true))
        return Transition(
            next,
            listOf(
                Cancel(TimerId.CANDIDATE_TIMEOUT),
                MachineEffect.ConfirmTrip(trip.id, manual = true),
                Mark(trip.id, nowNs, CaptureMarkType.CONFIRMED, WakeKind.MANUAL.name),
            ),
        )
    }

    // ---- fixes ----

    private fun onFixes(s: MachineState, e: MachineEvent.Fixes): Transition {
        val trip = s.trip
        if (!s.isActive() || trip == null) return Transition(s)
        val earliest = trip.header.startMonoNs - secondsNs(cfg.staleFixBeforeWakeS)
        var state = s
        val accepted = mutableListOf<LocationSample>()
        val fx = mutableListOf<MachineEffect>()
        for (fix in e.samples) {
            val last = state.lastFix
            val fresh = fix.tMonoNs >= earliest && (last == null || fix.tMonoNs > last.tMonoNs)
            if (fresh && state.isActive()) {
                accepted += fix
                state = track(state, fix)
                val t = afterFix(state, fix, e.nowNs)
                state = t.state
                fx += t.effects
            }
        }
        val appended = if (accepted.isEmpty()) emptyList() else listOf(Append(trip.id, CaptureBatch(fixes = accepted)))
        return Transition(state, appended + fx)
    }

    private fun track(s: MachineState, fix: LocationSample): MachineState {
        val speed = fix.speedMps
        val good = fix.hAccM?.let { it <= cfg.goodFixHAccM } ?: false
        val fast = speed != null && speed >= cfg.confirmSpeedMps
        return addDistance(s, fix).copy(
            lastFix = fix,
            goodFixSeen = s.goodFixSeen || good,
            maxSpeedMps = maxOf(s.maxSpeedMps, speed ?: 0.0),
            fastSinceNs = if (fast) s.fastSinceNs ?: fix.tMonoNs else null,
        )
    }

    private fun addDistance(s: MachineState, fix: LocationSample): MachineState {
        val gated = fix.hAccM?.let { it <= cfg.liveDistanceHAccM } ?: false
        val prev = s.distanceFix
        val step = prev?.let { Geo.haversineM(it.lat, it.lng, fix.lat, fix.lng) } ?: 0.0
        return when {
            !gated -> s
            prev == null -> s.copy(distanceFix = fix)
            step >= cfg.liveDistanceMinStepM -> s.copy(distanceM = s.distanceM + step, distanceFix = fix)
            else -> s
        }
    }

    private fun afterFix(s: MachineState, fix: LocationSample, nowNs: Long): Transition = when (s.phase) {
        CANDIDATE -> decideCandidate(s, nowNs)
        RECORDING -> stationaryCheck(s, fix, nowNs)
        PAUSED -> resumeCheck(s, fix, nowNs)
        else -> Transition(s)
    }

    // ---- candidate (§1.5) ----

    private fun decideCandidate(s: MachineState, nowNs: Long): Transition = when {
        confirms(s, nowNs) -> confirm(s, nowNs)
        rejects(s, nowNs) -> reject(s)
        else -> Transition(s)
    }

    private fun confirms(s: MachineState, nowNs: Long): Boolean {
        val fix = s.lastFix
        if (!s.goodFixSeen || fix == null) return false
        val held = s.fastSinceNs?.let { fix.tMonoNs - it >= secondsNs(cfg.confirmHoldS) } ?: false
        val bySpeed = !s.activity.isAtLeastMedium(ActivityType.CYCLING) && held
        val fresh = nowNs - fix.tMonoNs <= secondsNs(cfg.activityConfirmFixAgeS)
        val byActivity = s.activity.isAtLeastMedium(ActivityType.AUTOMOTIVE) &&
            (fix.speedMps ?: 0.0) >= cfg.activityConfirmSpeedMps && fresh
        return bySpeed || byActivity
    }

    private fun rejects(s: MachineState, nowNs: Long): Boolean {
        val start = s.trip?.header?.startMonoNs ?: return false
        return s.humanPowered() && nowNs - start >= secondsNs(cfg.rejectAfterS) && s.maxSpeedMps < cfg.rejectMaxSpeedMps
    }

    private fun confirm(s: MachineState, nowNs: Long): Transition {
        val trip = s.trip ?: return Transition(s)
        val header = trip.header
        val fx = buildList {
            add(Cancel(TimerId.CANDIDATE_TIMEOUT))
            add(MachineEffect.ConfirmTrip(trip.id, manual = false))
            add(Mark(trip.id, nowNs, CaptureMarkType.CONFIRMED))
            if (header.parking != null) add(MachineEffect.RunBackfill(trip.id, historyStart(header), header.startWallMs))
        }
        return Transition(s.copy(phase = RECORDING, trip = trip.copy(confirmed = true)), fx)
    }

    private fun reject(s: MachineState): Transition {
        val trip = s.trip ?: return Transition(s)
        val arm = armAt(s.parking)
        return Transition(idle(s, s.parking), sensorsOff() + Cancel(TimerId.CANDIDATE_TIMEOUT) + MachineEffect.Discard(trip.id) + arm)
    }

    // ---- stationary, resume and stop (§1.6) ----

    private fun stationaryCheck(s: MachineState, fix: LocationSample, nowNs: Long): Transition {
        val speed = fix.speedMps
        val slow = speed != null && speed < cfg.stationarySpeedMps
        if (!slow) return Transition(s.copy(slowFixes = 0, slowSinceNs = null, stoppingBeganAtNs = null))
        val slowSince = s.slowSinceNs ?: fix.tMonoNs
        val count = s.slowFixes + 1
        val platformStill = s.activity.isAtLeastMedium(ActivityType.STATIONARY)
        val began = s.stoppingBeganAtNs ?: if (count >= cfg.stationaryFixes || platformStill) slowSince else null
        val next = s.copy(slowFixes = count, slowSinceNs = slowSince, stoppingBeganAtNs = began)
        return if (began != null && fix.tMonoNs - began >= secondsNs(cfg.pauseAfterS)) pause(next, nowNs) else Transition(next)
    }

    private fun pause(s: MachineState, nowNs: Long): Transition {
        val trip = s.trip ?: return Transition(s)
        val began = s.stoppingBeganAtNs ?: nowNs
        val stopAt = began + secondsNs(stopSeconds(trip))
        return Transition(
            s.copy(phase = PAUSED, resumeFixes = 0),
            listOf(
                Mark(trip.id, began, CaptureMarkType.PAUSED),
                Schedule(TimerId.STOP, maxOf(0L, stopAt - nowNs)),
                Schedule(TimerId.IMU_IDLE, secondsNs(cfg.imuIdleAfterS)),
            ),
        )
    }

    private fun stopSeconds(trip: ActiveTrip): Double = if (trip.manual) cfg.manualStopS else cfg.autoStopS

    private fun resumeCheck(s: MachineState, fix: LocationSample, nowNs: Long): Transition {
        val fast = (fix.speedMps ?: 0.0) >= cfg.resumeSpeedMps
        val count = if (fast) s.resumeFixes + 1 else 0
        val next = s.copy(resumeFixes = count)
        return if (count >= cfg.resumeFixes && !s.humanPowered()) resume(next, nowNs) else Transition(next)
    }

    private fun resume(s: MachineState, nowNs: Long): Transition {
        val trip = s.trip ?: return Transition(s)
        val hz = rateFor(s.power)
        val rate = if (s.imuHz != hz && !s.imuStoppedForSpace) listOf(MachineEffect.SetImuRate(hz)) else emptyList()
        val next = s.copy(
            phase = RECORDING, slowFixes = 0, slowSinceNs = null, stoppingBeganAtNs = null, resumeFixes = 0,
            imuHz = if (s.imuStoppedForSpace) s.imuHz else hz,
        )
        return Transition(
            next,
            rate + Mark(trip.id, nowNs, CaptureMarkType.RESUMED) + Cancel(TimerId.STOP) + Cancel(TimerId.IMU_IDLE),
        )
    }

    // ---- motion: stillness, the no-GNSS rules and the IMU wake-up (§1.4, §1.6, §2.4) ----

    private fun onMotion(s: MachineState, e: MachineEvent.Motion): Transition {
        val trip = s.trip
        if (!s.isActive() || trip == null) return Transition(s)
        var still = s.still
        var wakeImu = false
        for (m in e.samples) {
            still = Stillness.add(still, m, cfg)
            if (s.isIdlingImu() && Stillness.magnitude(m.userAccelG) >= cfg.imuWakeG) wakeImu = true
        }
        var state = s.copy(still = still)
        val fx = mutableListOf<MachineEffect>()
        if (!s.imuStoppedForSpace && e.samples.isNotEmpty()) fx += Append(trip.id, CaptureBatch(motion = e.samples))
        if (wakeImu) {
            val hz = rateFor(s.power)
            state = state.copy(imuHz = hz)
            fx += listOf(MachineEffect.SetImuRate(hz), Mark(trip.id, e.nowNs, CaptureMarkType.IMU_RATE, hz.toString()))
            fx += Schedule(TimerId.IMU_IDLE, secondsNs(cfg.imuIdleAfterS))
        }
        val t = imuRules(state, e.nowNs)
        return Transition(t.state, fx + t.effects)
    }

    private fun imuRules(s: MachineState, nowNs: Long): Transition {
        val gnssStale = s.lastFix?.let { nowNs - it.tMonoNs > secondsNs(GNSS_STALE_S) } ?: true
        return when {
            s.phase == RECORDING && gnssStale -> {
                val since = s.still.stillSinceNs
                val stillLongEnough = since != null && nowNs - since >= secondsNs(cfg.pauseAfterS)
                if (stillLongEnough) pause(s.copy(stoppingBeganAtNs = since), nowNs) else Transition(s)
            }
            s.phase == PAUSED -> {
                val since = s.still.movingSinceNs
                val moving = since != null && nowNs - since >= secondsNs(cfg.resumeImuMovingS)
                if (moving && s.activity.isAtLeastMedium(ActivityType.AUTOMOTIVE)) resume(s, nowNs) else Transition(s)
            }
            else -> Transition(s)
        }
    }

    // ---- activity ----

    private fun onActivity(s: MachineState, samples: List<ActivitySample>, nowNs: Long): Transition {
        val latest = (listOfNotNull(s.activity) + samples).maxByOrNull { it.tMonoNs }
        val state = s.copy(activity = latest)
        val trip = state.trip
        if (samples.isEmpty() || !state.isActive() || trip == null) return Transition(state)
        val append = Append(trip.id, CaptureBatch(activity = samples))
        val t = when (state.phase) {
            CANDIDATE -> decideCandidate(state, nowNs)
            PAUSED -> imuRules(state, nowNs)
            else -> Transition(state)
        }
        return Transition(t.state, listOf(append) + t.effects)
    }

    // ---- power and storage (§1.4, §2.7, EC-BAT, EC-STO) ----

    private fun onPower(s: MachineState, e: MachineEvent.Power): Transition {
        val state = s.copy(power = e.status)
        val trip = state.trip
        val hz = rateFor(e.status)
        val keepRate = hz == state.imuHz || state.isIdlingImu() || state.imuStoppedForSpace
        return when {
            !state.isActive() || trip == null -> Transition(state)
            state.isLive() && isFlat(e.status) ->
                finish(state, e.nowNs, EndReason.LOW_BATTERY, interruption = InterruptionReason.LOW_BATTERY)
            keepRate -> Transition(state)
            else -> Transition(
                state.copy(imuHz = hz),
                listOf(MachineEffect.SetImuRate(hz), Mark(trip.id, e.nowNs, CaptureMarkType.IMU_RATE, hz.toString())),
            )
        }
    }

    private fun isFlat(p: PowerStatus): Boolean = !p.isCharging && (p.batteryPct ?: FULL) <= cfg.endBatteryPct

    private fun MachineState.isIdlingImu(): Boolean = phase == PAUSED && imuHz == cfg.imuIdleHz

    private fun onFreeSpace(s: MachineState, e: MachineEvent.FreeSpace): Transition {
        val trip = s.trip
        val needed = s.isActive() && !s.imuStoppedForSpace && e.bytes < cfg.minFreeBytes
        if (!needed || trip == null) return Transition(s)
        return Transition(
            s.copy(imuStoppedForSpace = true, imuHz = 0),
            listOf(MachineEffect.StopImu, Mark(trip.id, e.nowNs, CaptureMarkType.IMU_STOPPED, "disk")),
        )
    }

    // ---- timers ----

    private fun onTimer(s: MachineState, e: MachineEvent.Timer): Transition {
        val trip = s.trip ?: return Transition(s)
        val timedOut = e.nowNs - trip.header.startMonoNs >= secondsNs(cfg.candidateTimeoutS) - TIMER_SLACK_NS
        val canIdle = s.phase == PAUSED && !s.imuStoppedForSpace && s.imuHz != cfg.imuIdleHz
        return when {
            e.id == TimerId.CANDIDATE_TIMEOUT && s.phase == CANDIDATE && timedOut -> reject(s)
            e.id == TimerId.STOP && s.phase == PAUSED -> stopTimer(s, trip, e.nowNs)
            e.id == TimerId.IMU_IDLE && canIdle -> Transition(
                s.copy(imuHz = cfg.imuIdleHz),
                listOf(
                    MachineEffect.SetImuRate(cfg.imuIdleHz),
                    Mark(trip.id, e.nowNs, CaptureMarkType.IMU_RATE, cfg.imuIdleHz.toString()),
                ),
            )
            else -> Transition(s)
        }
    }

    private fun stopTimer(s: MachineState, trip: ActiveTrip, nowNs: Long): Transition {
        val began = s.stoppingBeganAtNs ?: return Transition(s)
        val due = began + secondsNs(stopSeconds(trip))
        return if (nowNs >= due - TIMER_SLACK_NS) finish(s, began, EndReason.STOP_TIMER) else Transition(s)
    }

    // ---- ending (§1.4) ----

    private fun onPassenger(s: MachineState, e: MachineEvent.Passenger): Transition {
        val trip = s.trip
        return when {
            !s.isLive() || trip == null -> Transition(s)
            e.keep -> finish(s.copy(trip = trip.copy(kind = TripKind.PASSENGER)), e.nowNs, EndReason.PASSENGER)
            else -> {
                val here = s.lastFix?.let { GeoPoint(it.lat, it.lng) }
                val arm = here?.let { listOf(MachineEffect.ArmParkedRegion(it, cfg.parkedRadiusM)) }.orEmpty()
                Transition(idle(s, s.parking), sensorsOff() + allTimersOff() + MachineEffect.Discard(trip.id) + arm)
            }
        }
    }

    private fun finish(
        s: MachineState,
        endNs: Long,
        reason: EndReason,
        interruption: InterruptionReason? = null,
    ): Transition {
        val trip = s.trip ?: return Transition(s)
        val fx = listOf(
            MachineEffect.StopImu,
            MachineEffect.StopActivity,
            MachineEffect.StopPower,
        ) + allTimersOff() + MachineEffect.Finalise(trip.id, endNs, trip.kind, reason, interruption)
        return Transition(s.copy(phase = FINISHING), fx)
    }

    private fun onFinalised(s: MachineState, e: MachineEvent.Finalised): Transition {
        if (s.phase != FINISHING) return Transition(s)
        val parking = e.parking ?: s.parking
        return Transition(idle(s, parking), listOf(MachineEffect.StopGnss) + armAt(parking))
    }

    // ---- helpers ----

    private fun idle(s: MachineState, parking: Parking?): MachineState =
        MachineState(autoDetect = s.autoDetect, parking = parking, activity = s.activity, power = s.power)

    private fun sensorsOn(hz: Int): List<MachineEffect> = listOf(
        MachineEffect.StartGnss,
        MachineEffect.StartImu(hz),
        MachineEffect.StartActivity,
        MachineEffect.StartPower,
    )

    private fun sensorsOff(): List<MachineEffect> = listOf(
        MachineEffect.StopGnss,
        MachineEffect.StopImu,
        MachineEffect.StopActivity,
        MachineEffect.StopPower,
    )

    private fun allTimersOff(): List<MachineEffect> = TimerId.entries.map { Cancel(it) }

    /** §2.7: 10 Hz when thermal is critical; 25 Hz in Low Power Mode, at ≤ 15 % battery or thermal serious. */
    fun rateFor(p: PowerStatus?): Int = when {
        p == null -> cfg.imuNominalHz
        p.thermal == ThermalState.CRITICAL -> cfg.imuIdleHz
        p.lowPowerMode || p.thermal == ThermalState.SERIOUS || (p.batteryPct ?: FULL) <= cfg.lowBatteryPct -> cfg.imuReducedHz
        else -> cfg.imuNominalHz
    }

    private fun MachineState.isActive(): Boolean = phase == CANDIDATE || phase == RECORDING || phase == PAUSED

    private fun MachineState.isLive(): Boolean = phase == RECORDING || phase == PAUSED

    private fun MachineState.humanPowered(): Boolean =
        activity.isAtLeastMedium(ActivityType.WALKING) ||
            activity.isAtLeastMedium(ActivityType.RUNNING) ||
            activity.isAtLeastMedium(ActivityType.CYCLING)

    private fun ActivitySample?.isAtLeastMedium(type: ActivityType): Boolean =
        this != null && this.type == type && confidence != ActivityConfidence.LOW

    private companion object {
        const val MS_PER_S = 1000.0
        const val GNSS_STALE_S = 3.0
        const val FULL = 100
        const val TIMER_SLACK_NS = 1_000_000_000L
    }
}

/** Seconds to nanoseconds. */
internal fun secondsNs(s: Double): Long = (s * 1e9).toLong()
