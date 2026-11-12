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
import com.driveperf.domain.capture.RearmReason
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
    private val cruiseRule = CruiseRule(cfg)

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
        is MachineEvent.Finalised -> onFinalised(s, e)
        is MachineEvent.LogFailed -> onLogFailed(s, e)
        is MachineEvent.AppendFailed -> onAppendFailed(s, e)
        else -> reduceDetection(s, e)
    }

    /** Automatic-detection controls and the F-CAP-14 check-ins. */
    private fun reduceDetection(s: MachineState, e: MachineEvent): Transition = when (e) {
        is MachineEvent.AutoDetect -> onAutoDetect(s, e)
        is MachineEvent.Foreground -> {
            val arm = if (s.phase == IDLE) armAt(s.parking) else emptyList()
            Transition(s, arm + checkIn(s))
        }
        is MachineEvent.WatchdogOpened -> Transition(s, listOf(MachineEffect.Track(CaptureEvent.watchdogOpened)))
        is MachineEvent.TriggersAvailable -> Transition(s.copy(triggersAvailable = e.available))
        is MachineEvent.InDriveVisible -> Transition(s.copy(inDriveVisible = e.visible))
        else -> Transition(s)
    }

    /** Turning detection on is a check-in and re-arms; turning it off removes the watchdog (F-CAP-15 AC2). */
    private fun onAutoDetect(s: MachineState, e: MachineEvent.AutoDetect): Transition {
        val next = s.copy(autoDetect = e.enabled)
        return when {
            e.enabled == s.autoDetect -> Transition(next)
            e.enabled -> Transition(next, (if (s.phase == IDLE) armAt(s.parking) else emptyList()) + MachineEffect.CheckIn)
            else -> Transition(next, listOf(MachineEffect.WatchdogOff))
        }
    }

    /** A check-in when automatic detection is on (F-CAP-14 AC1, AC4). */
    private fun checkIn(s: MachineState): List<MachineEffect> = if (s.autoDetect) listOf(MachineEffect.CheckIn) else emptyList()

    // ---- launch and recovery (§1.7) ----

    /**
     * After a reboot the stored parking spot is not trusted (EC-RBT): the phone may have been off while the car moved.
     * The parked region is still armed there, as the best guess for a wake, but the next trip starts at its first fix
     * with no estimated start (F-CAP-05) and no parking bound, until a trip end records a new spot.
     */
    private fun onLaunch(s: MachineState, e: MachineEvent.Launch): Transition {
        val stored = e.parking ?: s.parking
        val trusted = if (e.reason == RearmReason.BOOT) null else stored
        val base = s.copy(dbLocked = !e.dbAvailable, parking = trusted)
        val arm = armAt(stored) + MachineEffect.Track(CaptureEvent.rearmed(e.reason)) + checkIn(base)
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

    /** Every wake, even one that starts nothing, is a check-in: the app was woken (F-CAP-14 AC1). */
    private fun onWake(s: MachineState, e: MachineEvent.Wake): Transition {
        val manual = e.wake.kind == WakeKind.MANUAL
        val t = when {
            manual && s.phase == CANDIDATE -> promoteToManual(s, e)
            s.phase != IDLE || s.dbLocked -> Transition(s)
            manual -> startCapture(s, e, manual = true)
            !s.autoDetect || isStale(e) -> Transition(s)
            else -> startCapture(s, e, manual = false)
        }
        return Transition(t.state, checkIn(s) + t.effects)
    }

    private fun isStale(e: MachineEvent.Wake): Boolean = e.nowNs - e.wake.tMonoNs > secondsNs(cfg.staleWakeS)

    private fun startCapture(s: MachineState, e: MachineEvent.Wake, manual: Boolean): Transition {
        val header = e.newTrip
        val trip = ActiveTrip(header, manual = manual, confirmed = manual)
        val hz = rateFor(s.power)
        val next = idle(s, s.parking).copy(phase = if (manual) RECORDING else CANDIDATE, trip = trip, imuHz = hz)
        val historyFrom = historyStart(header)
        val fx = buildList {
            add(MachineEffect.CreateTrip(header, manual))
            addAll(sensorsOn(hz))
            add(Mark(trip.id, header.startMonoNs, CaptureMarkType.RECORDING_STARTED, e.wake.kind.name))
            add(MachineEffect.QueryActivityHistory(historyFrom, e.nowWallMs))
            if (!manual) add(Schedule(TimerId.CANDIDATE_TIMEOUT, secondsNs(cfg.candidateTimeoutS)))
            add(
                MachineEffect.Track(
                    if (manual) CaptureEvent.manualStarted(e.wake.sourceDetail) else CaptureEvent.autoWake(e.wake.kind),
                ),
            )
        }
        return Transition(next, fx)
    }

    /** The motion-start search never reaches before the last parking (Spike 1 change 3), nor 15 min (P-14). */
    private fun historyStart(header: CaptureHeader): Long {
        val window = header.startWallMs - (cfg.activityHistoryS * MS_PER_S).toLong()
        return maxOf(window, header.parking?.wallMs ?: window)
    }

    private fun promoteToManual(s: MachineState, e: MachineEvent.Wake): Transition {
        val nowNs = e.nowNs
        val trip = s.trip ?: return Transition(s)
        val next = s.copy(phase = RECORDING, trip = trip.copy(manual = true, confirmed = true))
        return Transition(
            next,
            listOf(
                Cancel(TimerId.CANDIDATE_TIMEOUT),
                MachineEffect.ConfirmTrip(trip.id, manual = true),
                Mark(trip.id, nowNs, CaptureMarkType.CONFIRMED, WakeKind.MANUAL.name),
                MachineEffect.Track(CaptureEvent.manualStarted(e.wake.sourceDetail)),
            ),
        )
    }

    /** No R0 means nothing would be kept: drop the trip before it shows as recording, and say so in the status. */
    private fun onLogFailed(s: MachineState, e: MachineEvent.LogFailed): Transition {
        val trip = s.trip
        if (trip == null || trip.id != e.tripId || !s.isActive()) return Transition(s)
        val fx = sensorsOff() + allTimersOff() + MachineEffect.Discard(trip.id) + armAt(s.parking)
        return Transition(idle(s, s.parking).copy(startFailed = true), fx)
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
        val rate = applyRate(state, e.nowNs)
        return Transition(rate.state, appended + fx + rate.effects)
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
        RECORDING -> {
            val t = stationaryCheck(s, fix, nowNs)
            val cruise = if (t.state.phase == RECORDING) cruiseRule.next(s.cruise, fix) else null
            Transition(t.state.copy(cruise = cruise), t.effects)
        }
        PAUSED -> resumeCheck(s, fix, nowNs)
        else -> Transition(s)
    }

    // ---- steady cruise (F-CAP-16 AC2–3) ----

    /**
     * The IMU rate the trip should run at, and why (§2.7, F-CAP-16 AC2): power and heat first, then steady cruise.
     * The idle rate of a long stop and the disk stop are handled where they happen.
     */
    private fun targetRate(s: MachineState): Pair<Int, RateReason> {
        val p = s.power
        val battery = p?.batteryPct ?: FULL
        return when {
            p?.thermal == ThermalState.CRITICAL -> cfg.imuIdleHz to RateReason.THERMAL
            p?.thermal == ThermalState.SERIOUS -> cfg.imuReducedHz to RateReason.THERMAL
            p?.lowPowerMode == true -> cfg.imuReducedHz to RateReason.LOW_POWER
            battery <= cfg.lowBatteryPct -> cfg.imuReducedHz to RateReason.BATTERY
            s.phase == RECORDING && s.cruise?.steady == true -> cfg.imuReducedHz to RateReason.CRUISE
            else -> cfg.imuNominalHz to RateReason.NOMINAL
        }
    }

    /** Moves the IMU to [targetRate] when it differs, with a mark for the quality notes and `imu_rate_changed`. */
    private fun applyRate(s: MachineState, nowNs: Long): Transition {
        val trip = s.trip
        val (hz, reason) = targetRate(s)
        val fixed = !s.isActive() || s.imuStoppedForSpace || s.isIdlingImu()
        return if (trip == null || fixed || hz == s.imuHz) {
            Transition(s)
        } else {
            Transition(s.copy(imuHz = hz), rateEffects(trip, hz, reason, nowNs))
        }
    }

    private fun rateEffects(trip: ActiveTrip, hz: Int, reason: RateReason, nowNs: Long): List<MachineEffect> = listOf(
        MachineEffect.SetImuRate(hz),
        Mark(trip.id, nowNs, CaptureMarkType.IMU_RATE, hz.toString()),
        MachineEffect.Track(CaptureEvent.imuRate(hz, reason)),
    )

    // ---- candidate (§1.5) ----

    private fun decideCandidate(s: MachineState, nowNs: Long): Transition = when {
        confirmsBySpeed(s) -> confirm(s, nowNs, byActivity = false)
        confirmsByActivity(s, nowNs) -> confirm(s, nowNs, byActivity = true)
        rejects(s, nowNs) -> reject(s, humanReason(s))
        else -> Transition(s)
    }

    /** Over 25 km/h held for 20 s, unless the platform says cycling (§1.5). Needs one good fix (EC-GPS). */
    private fun confirmsBySpeed(s: MachineState): Boolean {
        val fix = s.lastFix
        if (!s.goodFixSeen || fix == null) return false
        val held = s.fastSinceNs?.let { fix.tMonoNs - it >= secondsNs(cfg.confirmHoldS) } ?: false
        return !s.activity.isAtLeastMedium(ActivityType.CYCLING) && held
    }

    /** Automotive at medium or high confidence and over 10 km/h on a fresh fix (§1.5). */
    private fun confirmsByActivity(s: MachineState, nowNs: Long): Boolean {
        val fix = s.lastFix
        if (!s.goodFixSeen || fix == null) return false
        val fresh = nowNs - fix.tMonoNs <= secondsNs(cfg.activityConfirmFixAgeS)
        return s.activity.isAtLeastMedium(ActivityType.AUTOMOTIVE) &&
            (fix.speedMps ?: 0.0) >= cfg.activityConfirmSpeedMps && fresh
    }

    private fun humanReason(s: MachineState): RejectReason = when (s.activity?.type) {
        ActivityType.RUNNING -> RejectReason.RUNNING
        ActivityType.CYCLING -> RejectReason.CYCLING
        else -> RejectReason.WALKING
    }

    private fun rejects(s: MachineState, nowNs: Long): Boolean {
        val start = s.trip?.header?.startMonoNs ?: return false
        return s.humanPowered() && nowNs - start >= secondsNs(cfg.rejectAfterS) && s.maxSpeedMps < cfg.rejectMaxSpeedMps
    }

    private fun confirm(s: MachineState, nowNs: Long, byActivity: Boolean): Transition {
        val trip = s.trip ?: return Transition(s)
        val header = trip.header
        val fx = buildList {
            add(Cancel(TimerId.CANDIDATE_TIMEOUT))
            add(MachineEffect.ConfirmTrip(trip.id, manual = false))
            add(Mark(trip.id, nowNs, CaptureMarkType.CONFIRMED))
            add(MachineEffect.Track(CaptureEvent.confirmed(byActivity)))
            add(MachineEffect.Track(CaptureEvent.autoStarted(header.wakeKind)))
            if (header.parking != null) add(MachineEffect.RunBackfill(trip.id, historyStart(header), header.startWallMs))
        }
        return Transition(s.copy(phase = RECORDING, trip = trip.copy(confirmed = true)), fx)
    }

    private fun reject(s: MachineState, reason: RejectReason): Transition {
        val trip = s.trip ?: return Transition(s)
        val arm = armAt(s.parking)
        val fx = sensorsOff() + Cancel(TimerId.CANDIDATE_TIMEOUT) + MachineEffect.Discard(trip.id) + arm +
            MachineEffect.Track(CaptureEvent.rejected(reason))
        return Transition(idle(s, s.parking), fx)
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
            s.copy(phase = PAUSED, resumeFixes = 0, cruise = null),
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
        val (hz, reason) = targetRate(s.copy(cruise = null))
        val rate = if (s.imuHz != hz && !s.imuStoppedForSpace) rateEffects(trip, hz, reason, nowNs) else emptyList()
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
        var manoeuvre = false
        for (m in e.samples) {
            still = Stillness.add(still, m, cfg)
            val a = Stillness.magnitude(m.userAccelG)
            if (s.isIdlingImu() && a >= cfg.imuWakeG) wakeImu = true
            if (a >= cfg.cruiseExitAccelG) manoeuvre = true
        }
        var state = s.copy(still = still, cruise = if (manoeuvre) null else s.cruise)
        val fx = mutableListOf<MachineEffect>()
        if (!s.imuStoppedForSpace && e.samples.isNotEmpty()) fx += Append(trip.id, CaptureBatch(motion = e.samples))
        if (wakeImu) {
            val (hz, reason) = targetRate(state)
            state = state.copy(imuHz = hz)
            fx += rateEffects(trip, hz, reason, e.nowNs)
            fx += Schedule(TimerId.IMU_IDLE, secondsNs(cfg.imuIdleAfterS))
        }
        val t = imuRules(state, e.nowNs)
        val rate = applyRate(t.state, e.nowNs)
        return Transition(rate.state, fx + t.effects + rate.effects)
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
        return when {
            !state.isActive() || trip == null -> Transition(state)
            state.isLive() && isFlat(e.status) ->
                finish(state, e.nowNs, EndReason.LOW_BATTERY, interruption = InterruptionReason.LOW_BATTERY)
            else -> {
                val noted = powerMarks(state, trip, e)
                val rate = applyRate(noted.state, e.nowNs)
                Transition(rate.state, noted.effects + rate.effects)
            }
        }
    }

    /**
     * Data-quality notes (F-CAP-16 AC2): a THERMAL mark whenever the thermal state changes during a trip (from the
     * first non-nominal one), and one LOW_BATTERY mark when the battery first reaches the low level unplugged.
     */
    private fun powerMarks(s: MachineState, trip: ActiveTrip, e: MachineEvent.Power): Transition {
        val p = e.status
        val thermalChanged = p.thermal != (s.markedThermal ?: ThermalState.NOMINAL)
        val low = !p.isCharging && (p.batteryPct ?: FULL) <= cfg.lowBatteryPct && !s.markedLowBattery
        val fx = buildList {
            if (thermalChanged) add(Mark(trip.id, e.nowNs, CaptureMarkType.THERMAL, p.thermal.name.lowercase()))
            if (low) add(Mark(trip.id, e.nowNs, CaptureMarkType.LOW_BATTERY, p.batteryPct.toString()))
        }
        return Transition(s.copy(markedThermal = p.thermal, markedLowBattery = s.markedLowBattery || low), fx)
    }

    private fun isFlat(p: PowerStatus): Boolean = !p.isCharging && (p.batteryPct ?: FULL) <= cfg.endBatteryPct

    private fun MachineState.isIdlingImu(): Boolean = phase == PAUSED && imuHz == cfg.imuIdleHz

    /**
     * EC-STO: the first failed write stops the IMU, which is most of the volume, and keeps GNSS. A write without IMU
     * samples that still fails after that means nothing more can be kept: a trip ends with what R0 holds, a candidate
     * is dropped. A failed IMU batch after the stop was queued before it and is ignored.
     */
    private fun onAppendFailed(s: MachineState, e: MachineEvent.AppendFailed): Transition {
        val trip = s.trip
        return when {
            trip == null || trip.id != e.tripId || !s.isActive() -> Transition(s)
            !s.imuStoppedForSpace -> onFreeSpace(s, MachineEvent.FreeSpace(e.nowNs, 0L))
            e.hadMotion -> Transition(s)
            s.phase == CANDIDATE -> reject(s, RejectReason.STORAGE)
            else -> finish(s, e.nowNs, EndReason.STORAGE)
        }
    }

    private fun onFreeSpace(s: MachineState, e: MachineEvent.FreeSpace): Transition {
        val trip = s.trip
        val needed = s.isActive() && !s.imuStoppedForSpace && e.bytes < cfg.minFreeBytes
        if (!needed || trip == null) return Transition(s)
        return Transition(
            s.copy(imuStoppedForSpace = true, imuHz = 0),
            listOf(
                MachineEffect.StopImu,
                Mark(trip.id, e.nowNs, CaptureMarkType.IMU_STOPPED, "disk"),
                MachineEffect.Track(CaptureEvent.imuRate(0, RateReason.STORAGE)),
            ),
        )
    }

    // ---- timers ----

    private fun onTimer(s: MachineState, e: MachineEvent.Timer): Transition {
        val trip = s.trip ?: return Transition(s)
        val timedOut = e.nowNs - trip.header.startMonoNs >= secondsNs(cfg.candidateTimeoutS) - TIMER_SLACK_NS
        val canIdle = s.phase == PAUSED && !s.imuStoppedForSpace && s.imuHz != cfg.imuIdleHz
        return when {
            e.id == TimerId.CANDIDATE_TIMEOUT && s.phase == CANDIDATE && timedOut -> reject(s, RejectReason.TIMEOUT)
            e.id == TimerId.STOP && s.phase == PAUSED -> stopTimer(s, trip, e.nowNs)
            e.id == TimerId.IMU_IDLE && canIdle -> Transition(
                s.copy(imuHz = cfg.imuIdleHz),
                rateEffects(trip, cfg.imuIdleHz, RateReason.STATIONARY, e.nowNs),
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
        val track = reason.analyticsKey?.let { listOf(MachineEffect.Track(CaptureEvent.ended(it))) }.orEmpty()
        return Transition(s.copy(phase = FINISHING), track + fx)
    }

    private fun onFinalised(s: MachineState, e: MachineEvent.Finalised): Transition {
        if (s.phase != FINISHING) return Transition(s)
        val parking = e.parking ?: s.parking
        return Transition(idle(s, parking), listOf(MachineEffect.StopGnss) + armAt(parking) + checkIn(s))
    }

    // ---- helpers ----

    private fun idle(s: MachineState, parking: Parking?): MachineState = MachineState(
        autoDetect = s.autoDetect,
        parking = parking,
        activity = s.activity,
        power = s.power,
        triggersAvailable = s.triggersAvailable,
    )

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
