package com.driveperf.di

import com.driveperf.data.local.DatabaseDriverFactory
import com.driveperf.data.local.LocalEventFeedbackStore
import com.driveperf.data.local.LocalRouteHistory
import com.driveperf.data.local.LocalRouteRepository
import com.driveperf.data.local.LocalTripHistory
import com.driveperf.data.local.LocalTripRepository
import com.driveperf.data.local.LocalTripStore
import com.driveperf.data.local.ProtoAnalysisFileStore
import com.driveperf.data.local.ProtoCaptureLog
import com.driveperf.data.local.ProtoPreviewStore
import com.driveperf.data.local.openDatabase
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.hello.GetHelloMessage
import com.driveperf.domain.hello.HelloMessage
import com.driveperf.foundation.platformDescription
import com.driveperf.pipeline.PipelineFinaliser
import com.driveperf.pipeline.capture.CapturePersistence
import com.driveperf.pipeline.capture.CaptureConfig
import com.driveperf.pipeline.capture.CapturePorts
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.capture.TripRecorderEngine
import com.driveperf.pipeline.score.FeedbackRescorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * The single entry point apps use to reach the shared core (ADR-002 section 5). Platform code calls `start` once at
 * launch: Android through `DrivePerfCore.start(platform, context)` in `androidMain`, iOS through
 * `DrivePerfCore.start(platform:)` in `iosMain`. Then it calls `services.recorder.onLaunch()`.
 */
public object DrivePerfCore {
    private var started: CoreServices? = null

    /** The started core. Throws when `start` has not run: a programmer error, not an expected failure. */
    public val services: CoreServices
        get() = checkNotNull(started) { "DrivePerfCore.start must run first" }

    /** The "Hello DrivePerf" screen content, computed by shared code on the current platform. */
    public fun hello(): HelloMessage = GetHelloMessage()(platformDescription())

    /** Wires the core on [database]. Calling it again returns the first instance. */
    internal fun startWith(
        platform: PlatformBindings,
        database: DatabaseDriverFactory,
        io: CoroutineContext,
        finaliser: ((com.driveperf.data.local.db.DrivePerfDatabase) -> TripFinaliser)? = null,
    ): CoreServices = started ?: build(platform, database, io, finaliser).also { started = it }

    /**
     * A separate core on its own [database] that never becomes [services], for the on-device self-test: it must not
     * touch the user's trips (spike change 10). [capture] shortens its timers. Close it when done.
     */
    internal fun scratch(
        platform: PlatformBindings,
        database: DatabaseDriverFactory,
        io: CoroutineContext,
        capture: CaptureConfig,
    ): CoreServices = build(platform, database, io, null, capture)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun build(
        platform: PlatformBindings,
        database: DatabaseDriverFactory,
        io: CoroutineContext,
        finaliser: ((com.driveperf.data.local.db.DrivePerfDatabase) -> TripFinaliser)?,
        capture: CaptureConfig = CaptureConfig(),
    ): CoreServices {
        val driver = database.create()
        val db = openDatabase(driver)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val store = LocalTripStore(db, platform.deviceId(), io)
        val files = platform.tripFiles()
        val clock = platform.clock()
        val previews = ProtoPreviewStore(files, io)
        val history = LocalTripHistory(db, io)
        val recorder = TripRecorderEngine(
            ports = CapturePorts(
                location = platform.location(),
                motion = platform.motion(),
                activity = platform.activity(),
                power = platform.power(),
                parkedRegion = platform.parkedRegion(),
                files = files,
            ),
            persistence = CapturePersistence(
                store,
                ProtoCaptureLog(files, io),
                finaliser?.invoke(db) ?: PipelineFinaliser(history, LocalRouteHistory(db, previews, io)),
                previews,
                ProtoAnalysisFileStore(files, io),
            ),
            clock = clock,
            zones = SystemTimeZone(),
            scope = scope,
            config = capture,
        )
        val trips = LocalTripRepository(db, files, io)
        val routes = LocalRouteRepository(db, io)
        return CoreServices(
            recorder = recorder,
            trips = trips,
            routes = routes,
            markEventNeeded = MarkEventNeeded(
                trips, routes, LocalEventFeedbackStore(db, io), FeedbackRescorer(history, previews), clock,
            ),
            onClose = {
                scope.cancel()
                driver.close()
            },
        )
    }

    /** Forgets the started core. Tests only. */
    internal fun reset() {
        started = null
    }
}
