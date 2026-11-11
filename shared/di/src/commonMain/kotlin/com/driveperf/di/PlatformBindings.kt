package com.driveperf.di

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.repository.RouteRepository
import com.driveperf.domain.repository.TripRepository
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock

/**
 * The natively implemented ports (ADR-002 section 5). Swift supplies its adapters at app launch; Android supplies
 * them from `:apps:android:platform`. Each function is called once, at start.
 */
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
}

/**
 * What the apps use after start. [markEventNeeded] is "Was it needed?" / "Count it again" (F-SCO-05); feedback is read
 * through [trips].
 */
public class CoreServices internal constructor(
    public val recorder: TripRecorder,
    public val trips: TripRepository,
    public val routes: RouteRepository,
    public val markEventNeeded: MarkEventNeeded,
    /** Usage statistics behind the user's choice (F-ONB-08): events wait until the choice, and are dropped if declined. */
    public val analytics: Analytics,
    private val onClose: () -> Unit,
) {
    /** Stops the recorder's work and closes the database. Only for a scratch core; the app's core lives as long as the process. */
    public fun close() {
        onClose()
    }
}
