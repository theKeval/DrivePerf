package com.driveperf.di

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.AutoDetectResumeNotice
import com.driveperf.domain.capture.AutoDetectWatchdog
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
import com.driveperf.domain.capture.RecorderState
import com.driveperf.presentation.replay.ReplayPresenter
import com.driveperf.presentation.replay.ReplaySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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
}

/**
 * What the apps use after start. [markEventNeeded] is "Was it needed?" / "Count it again" (F-SCO-05); feedback is read
 * through [trips]. [corrections] answers "Was this a bus or train?" and corrects what a trip was (F-CAP-04).
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
    private val clock: Clock,
    private val onClose: () -> Unit,
) {
    /**
     * The shared Replay presenter for [tripId] (S-RPL-01), running in a child of [scope]. Android passes
     * `viewModelScope`. Call `close()` when the screen goes away.
     */
    public fun replayPresenter(tripId: String, source: ReplaySource, scope: CoroutineScope): ReplayPresenter =
        ReplayPresenter(
            tripId = tripId,
            source = source,
            replays = replay,
            isRecording = { recorder.status.value.let { it.tripId == tripId && it.state != RecorderState.IDLE } },
            analytics = analytics,
            monoNowNs = clock::monotonicNs,
            scope = scope,
        )

    /** [replayPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
    public fun replayPresenter(tripId: String, source: ReplaySource): ReplayPresenter =
        replayPresenter(tripId, source, CoroutineScope(Dispatchers.Main))

    /** Stops the recorder's work and closes the database. Only for a scratch core; the app's core lives as long as the process. */
    public fun close() {
        onClose()
    }
}
