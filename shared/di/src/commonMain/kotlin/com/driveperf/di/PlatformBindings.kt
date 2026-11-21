package com.driveperf.di

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.AutoDetectResumeNotice
import com.driveperf.domain.capture.AutoDetectWatchdog
import com.driveperf.domain.capture.CaptureDeadlineAlarm
import com.driveperf.domain.capture.NoOpCaptureDeadlineAlarm
import com.driveperf.domain.capture.NoOpAutoDetectResumeNotice
import com.driveperf.domain.capture.NoOpAutoDetectWatchdog
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.trip.TripCorrections
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.capture.PreviewStore
import com.driveperf.domain.feedback.EventFeedbackStore
import com.driveperf.domain.feedback.TripRescorer
import com.driveperf.domain.score.TripRecompute
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.tripfile.AnalysisFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * The natively implemented ports (ADR-002 section 5). Swift supplies its adapters at app launch; Android supplies
 * them from `:apps:android:platform`. Each function is called once, at start.
 */
@Suppress("TooManyFunctions") // one function per native port
public interface PlatformBindings {
    public fun clock(): Clock

    public fun location(): LocationSource

    public fun motion(): MotionSource

    public fun activity(): ActivitySource

    public fun power(): PowerState

    public fun parkedRegion(): ParkedRegionMonitor

    public fun tripFiles(): TripFileStore

    /** This install's UUIDv7 device ID, kept in the Keychain or encrypted preferences (data-model.md §1.4). */
    public fun deviceId(): String

    /**
     * The analytics vendor behind the `Analytics` port (Firebase Analytics, ADR-007). Builds with no vendor configured
     * return `NoOpAnalyticsSink`. The sink does no consent logic: the core wraps it in the consent gate.
     */
    public fun analyticsSink(): AnalyticsSink

    /**
     * The auto-detect watchdog notification (F-CAP-14 AC1). iOS supplies its `UNUserNotificationCenter` adapter;
     * Android keeps the default, which schedules nothing.
     */
    public fun watchdog(): AutoDetectWatchdog = NoOpAutoDetectWatchdog

    /**
     * The "Auto-detect is back on" notification at the end of a pause (F-CAP-15 AC1). iOS supplies its
     * `UNUserNotificationCenter` adapter, Android an `AlarmManager` one. The default schedules nothing.
     */
    public fun resumeNotice(): AutoDetectResumeNotice = NoOpAutoDetectResumeNotice

    /**
     * The device wake behind the trip's stop timer (DRIVE-223). Android supplies an allow-while-idle `AlarmManager`
     * alarm; iOS keeps the default, which schedules nothing.
     */
    public fun stopAlarm(): CaptureDeadlineAlarm = NoOpCaptureDeadlineAlarm
}

/** The core's own wiring that the per-feature factories need. Built only by [DrivePerfCore]. */
@Suppress("LongParameterList") // one property per store, like CoreServices
internal class CoreInternals(
    val db: DrivePerfDatabase,
    val io: CoroutineContext,
    val files: TripFileStore,
    val previews: PreviewStore,
    val history: TripHistory,
    val analysisFiles: AnalysisFileStore,
    val feedbackStore: EventFeedbackStore,
    val rescorer: TripRescorer,
    /** DRIVE-131: re-derives the scores of a trip and every later one (§5.7 "Corrections"). */
    val recompute: TripRecompute,
)

/**
 * What the apps use after start. [markEventNeeded] is "Was it needed?" / "Count it again" (F-SCO-05); feedback is read
 * through [trips]. [corrections] answers "Was this a bus or train?" and corrects what a trip was (F-CAP-04).
 *
 * The screen presenters are extensions, one file per feature (`DrivesServices.kt`, `ReplayServices.kt`,
 * `AnalysisServices.kt`); Swift still sees them as methods. They build from the internal accessors below.
 */
@Suppress("LongParameterList") // The core's ports for the apps: one property each, built only by DrivePerfCore.
public class CoreServices internal constructor(
    public val recorder: TripRecorder,
    public val trips: TripRepository,
    public val routes: RouteRepository,
    /** Trip Replay and its data-quality overlay (F-RPL-01, F-RPL-08). */
    public val replay: ReplayRepository,
    public val markEventNeeded: MarkEventNeeded,
    public val corrections: TripCorrections,
    /** Usage statistics behind the user's choice (F-ONB-08): events wait until the choice, and are dropped if declined. */
    public val analytics: Analytics,
    public val settings: com.driveperf.domain.settings.SettingsRepository,
    internal val clock: Clock,
    /**
     * What the per-feature factories (`DrivesServices.kt`, `ReplayServices.kt`, `AnalysisServices.kt`) build from.
     * Internal, so Swift never sees it.
     */
    private val internals: CoreInternals,
    private val onClose: () -> Unit,
) {
    internal val db: DrivePerfDatabase get() = internals.db
    internal val io: CoroutineContext get() = internals.io
    internal val files: TripFileStore get() = internals.files
    internal val previews: PreviewStore get() = internals.previews
    internal val history: TripHistory get() = internals.history
    internal val analysisFiles: AnalysisFileStore get() = internals.analysisFiles
    internal val feedbackStore: EventFeedbackStore get() = internals.feedbackStore
    internal val rescorer: TripRescorer get() = internals.rescorer
    internal val recompute: TripRecompute get() = internals.recompute

    /** The user's unit system, or the region's default until they choose one. */
    internal fun units(region: String): Flow<UnitSystem> = settings.state.map { it.effectiveUnits(region) }

    /** Stops the recorder's work and closes the database. Only for a scratch core; the app's core lives as long as the process. */
    public fun close() {
        onClose()
    }
}
