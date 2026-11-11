package com.driveperf.di

import com.driveperf.data.local.DatabaseDriverFactory
import com.driveperf.data.local.LocalRouteRepository
import com.driveperf.data.local.LocalTripRepository
import com.driveperf.data.local.LocalTripStore
import com.driveperf.data.local.ProtoCaptureLog
import com.driveperf.data.local.openDatabase
import com.driveperf.domain.hello.GetHelloMessage
import com.driveperf.domain.hello.HelloMessage
import com.driveperf.foundation.platformDescription
import com.driveperf.pipeline.capture.CapturePersistence
import com.driveperf.pipeline.capture.CapturePorts
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.capture.TripRecorderEngine
import com.driveperf.pipeline.capture.TripSummariser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun startWith(
        platform: PlatformBindings,
        database: DatabaseDriverFactory,
        io: CoroutineContext,
        finaliser: TripFinaliser = TripSummariser(),
    ): CoreServices = started ?: run {
        val db = openDatabase(database.create())
        val store = LocalTripStore(db, platform.deviceId(), io)
        val recorder = TripRecorderEngine(
            ports = CapturePorts(
                location = platform.location(),
                motion = platform.motion(),
                activity = platform.activity(),
                power = platform.power(),
                parkedRegion = platform.parkedRegion(),
                files = platform.tripFiles(),
            ),
            persistence = CapturePersistence(store, ProtoCaptureLog(platform.tripFiles(), io), finaliser),
            clock = platform.clock(),
            zones = SystemTimeZone(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)),
        )
        CoreServices(
            recorder = recorder,
            trips = LocalTripRepository(db, platform.tripFiles(), io),
            routes = LocalRouteRepository(db, io),
        ).also { started = it }
    }

    /** Forgets the started core. Tests only. */
    internal fun reset() {
        started = null
    }
}
