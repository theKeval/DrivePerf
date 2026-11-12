package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.TripKind
import kotlin.math.sqrt

/** The machine's timers. Times are monotonic and come back as [MachineEvent.Timer]. */
internal enum class TimerId { CANDIDATE_TIMEOUT, STOP, IMU_IDLE }

/** Why a trip is being finished. */
internal enum class EndReason(val analyticsKey: String?) {
    STOP_TIMER("stop_timer"),
    USER_STOP("user"),
    PASSENGER("user"),
    LOW_BATTERY("battery"),

    /** R0 can no longer be written at all, after the IMU was already stopped for space (EC-STO). */
    STORAGE("storage"),

    /** Not an end the user sees as one: the trip was found after a kill. No `trip_ended`. */
    RECOVERED_INTERRUPTED(null),
}

/** The trip being captured. [startMonoNs] is `t = 0`: the wake, so nothing after the trigger is lost (Spike 1). */
internal data class ActiveTrip(
    val header: CaptureHeader,
    val manual: Boolean,
    val confirmed: Boolean,
    val kind: TripKind = TripKind.DRIVER,
) {
    val id: String get() = header.tripId
}

/** IMU stillness over consecutive 2 s windows (§2.4). */
internal data class Stillness(
    val windowStartNs: Long? = null,
    val n: Int = 0,
    val sum: Double = 0.0,
    val sumSq: Double = 0.0,
    val maxGyroDps: Double = 0.0,
    /** Since when every closed window was still; null while moving or unknown. */
    val stillSinceNs: Long? = null,
    /** Since when every closed window was not still; null while still or unknown. */
    val movingSinceNs: Long? = null,
) {
    companion object {
        /** Adds one sample; closes the window once it spans [CaptureConfig.stillWindowS]. */
        fun add(w: Stillness, m: MotionSample, cfg: CaptureConfig): Stillness {
            val start = w.windowStartNs ?: m.tMonoNs
            val a = magnitude(m.userAccelG)
            val gyro = magnitude(m.rotRateDps)
            val open = w.copy(
                windowStartNs = start,
                n = w.n + 1,
                sum = w.sum + a,
                sumSq = w.sumSq + a * a,
                maxGyroDps = maxOf(w.maxGyroDps, gyro),
            )
            if (m.tMonoNs - start < secondsNs(cfg.stillWindowS)) return open
            val mean = open.sum / open.n
            val variance = maxOf(0.0, open.sumSq / open.n - mean * mean)
            val still = sqrt(variance) < cfg.stillAccelStdG && open.maxGyroDps < cfg.stillGyroDps
            return Stillness(
                stillSinceNs = if (still) w.stillSinceNs ?: start else null,
                movingSinceNs = if (still) null else w.movingSinceNs ?: start,
            )
        }

        /** Euclidean norm. */
        fun magnitude(v: Vec3): Double = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    }
}

/** Everything the reducer knows. Immutable; one instance per transition. */
internal data class MachineState(
    val phase: RecorderState = RecorderState.IDLE,
    val autoDetect: Boolean = true,
    val dbLocked: Boolean = false,
    val parking: Parking? = null,
    val trip: ActiveTrip? = null,
    val activity: ActivitySample? = null,
    val lastFix: LocationSample? = null,
    val goodFixSeen: Boolean = false,
    val fastSinceNs: Long? = null,
    val maxSpeedMps: Double = 0.0,
    val slowFixes: Int = 0,
    val slowSinceNs: Long? = null,
    val stoppingBeganAtNs: Long? = null,
    val resumeFixes: Int = 0,
    val imuHz: Int = 0,
    val imuStoppedForSpace: Boolean = false,
    val power: PowerStatus? = null,
    val still: Stillness = Stillness(),
    val distanceM: Double = 0.0,
    val distanceFix: LocationSample? = null,
    /** The last start was dropped because R0 could not be created. */
    val startFailed: Boolean = false,
    /** False while the platform reports that its OS triggers cannot run (F-CAP-14 health). */
    val triggersAvailable: Boolean = true,
)

/** Inputs to the reducer. Every event carries the monotonic time it is handled at, so the reducer reads no clock. */
internal sealed interface MachineEvent {
    val nowNs: Long

    /** Process start. [recovering] is the trip still `RECORDING` after a kill (§1.7). */
    data class Launch(
        override val nowNs: Long,
        val nowWallMs: Long,
        val dbAvailable: Boolean,
        val parking: Parking?,
        val recovering: Recovery?,
        val reason: RearmReason = RearmReason.LAUNCH,
    ) : MachineEvent

    /** The app came to the foreground (F-CAP-14 check-in). */
    data class Foreground(override val nowNs: Long) : MachineEvent

    /** The user opened the app from the watchdog notification. */
    data class WatchdogOpened(override val nowNs: Long) : MachineEvent

    /** The platform reports whether its OS triggers can run. */
    data class TriggersAvailable(override val nowNs: Long, val available: Boolean) : MachineEvent

    /** An R0 append for [tripId] failed (disk full or I/O error, EC-STO). */
    data class AppendFailed(override val nowNs: Long, val tripId: String) : MachineEvent

    /** A wake. [newTrip] is the header to use if it starts a trip (ID, zone and parking filled in by the caller). */
    data class Wake(override val nowNs: Long, val nowWallMs: Long, val wake: WakeEvent, val newTrip: CaptureHeader) :
        MachineEvent

    data class Fixes(override val nowNs: Long, val samples: List<LocationSample>) : MachineEvent

    data class Motion(override val nowNs: Long, val samples: List<MotionSample>) : MachineEvent

    data class Activity(override val nowNs: Long, val samples: List<ActivitySample>) : MachineEvent

    /** The answer to [MachineEffect.QueryActivityHistory]. */
    data class ActivityHistory(override val nowNs: Long, val samples: List<ActivitySample>) : MachineEvent

    data class Power(override val nowNs: Long, val status: PowerStatus) : MachineEvent

    data class FreeSpace(override val nowNs: Long, val bytes: Long) : MachineEvent

    data class Timer(override val nowNs: Long, val id: TimerId) : MachineEvent

    data class UserStop(override val nowNs: Long) : MachineEvent

    data class Passenger(override val nowNs: Long, val keep: Boolean) : MachineEvent

    data class AutoDetect(override val nowNs: Long, val enabled: Boolean) : MachineEvent

    /** R0 could not be created for [tripId], so nothing it records would be kept. */
    data class LogFailed(override val nowNs: Long, val tripId: String) : MachineEvent

    /** Finalisation committed. [parking] is the trip's end point, when it has one. */
    data class Finalised(override val nowNs: Long, val parking: Parking?) : MachineEvent
}

/** A trip found `RECORDING` at launch. [lastSampleWallMs] is the time of the last R0 sample. */
internal data class Recovery(
    val header: CaptureHeader,
    val manual: Boolean,
    val confirmed: Boolean,
    val lastSampleWallMs: Long,
    val wasMoving: Boolean,
)

/** Outputs of the reducer, as data (§1.1). The recorder executes them against the ports. */
internal sealed interface MachineEffect {
    data object StartGnss : MachineEffect

    data object StopGnss : MachineEffect

    data class StartImu(val hz: Int) : MachineEffect

    data class SetImuRate(val hz: Int) : MachineEffect

    data object StopImu : MachineEffect

    data object StartActivity : MachineEffect

    data object StopActivity : MachineEffect

    data object StartPower : MachineEffect

    data object StopPower : MachineEffect

    /** Creates the trip row and the R0 log. */
    data class CreateTrip(val header: CaptureHeader, val manual: Boolean) : MachineEffect

    /** The candidate became a trip (or a candidate turned manual): update the row. */
    data class ConfirmTrip(val tripId: String, val manual: Boolean) : MachineEffect

    data class QueryActivityHistory(val fromWallMs: Long, val toWallMs: Long) : MachineEffect

    /** Reads recorded accelerometer history for the estimated start into R0 (§2.8). Never scored (D6). */
    data class RunBackfill(val tripId: String, val fromWallMs: Long, val toWallMs: Long) : MachineEffect

    /** Appends samples to R0. */
    data class Append(val tripId: String, val batch: CaptureBatch) : MachineEffect

    data class Mark(val tripId: String, val tNs: Long, val type: CaptureMarkType, val detail: String? = null) :
        MachineEffect

    data class Schedule(val id: TimerId, val delayNs: Long) : MachineEffect

    data class Cancel(val id: TimerId) : MachineEffect

    data class ArmParkedRegion(val center: GeoPoint, val radiusM: Double) : MachineEffect

    /** Runs the batch pipeline and commits the trip. [endNs] null means "at the last R0 sample" (§1.7). */
    data class Finalise(
        val tripId: String,
        val endNs: Long?,
        val kind: TripKind,
        val reason: EndReason,
        val interruption: InterruptionReason?,
    ) : MachineEffect

    /** Deletes a rejected candidate or an unsaved passenger trip now. */
    data class Discard(val tripId: String) : MachineEffect

    /** Sends a capture analytics event. */
    data class Track(val event: CaptureEvent) : MachineEffect

    /** A check-in (F-CAP-14): records the time and moves the watchdog notification ahead. */
    data object CheckIn : MachineEffect

    /** Automatic detection went off: removes the watchdog notification. */
    data object WatchdogOff : MachineEffect
}

/** One reducer step. */
internal data class Transition(val state: MachineState, val effects: List<MachineEffect> = emptyList())
