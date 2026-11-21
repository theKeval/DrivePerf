package com.driveperf.di

import com.driveperf.data.local.DatabaseDriverFactory
import com.driveperf.data.local.LocalEventFeedbackStore
import com.driveperf.data.local.LocalRouteHistory
import com.driveperf.data.local.LocalRouteRepository
import com.driveperf.data.local.LocalScoreHistoryStore
import com.driveperf.data.local.LocalSettingsRepository
import com.driveperf.data.local.LocalTripCorrections
import com.driveperf.data.local.LocalTripHistory
import com.driveperf.data.local.LocalTripRepository
import com.driveperf.data.local.LocalTripStore
import com.driveperf.data.local.LocalAutoDetectPauseStore
import com.driveperf.data.local.LocalWatchdogStore
import com.driveperf.data.local.ProtoAnalysisFileStore
import com.driveperf.data.local.ProtoCaptureLog
import com.driveperf.data.local.ProtoPreviewStore
import com.driveperf.data.local.RecomputingTripRepository
import com.driveperf.data.local.openDatabase
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.analytics.ConsentGatedAnalytics
import com.driveperf.domain.feedback.MarkEventNeeded
import com.driveperf.domain.hello.GetHelloMessage
import com.driveperf.domain.hello.HelloMessage
import com.driveperf.domain.replay.TripReplayRepository
import com.driveperf.domain.repository.TripHistory
import com.driveperf.domain.score.TripRecompute
import com.driveperf.foundation.Clock
import com.driveperf.foundation.platformDescription
import com.driveperf.pipeline.PipelineFinaliser
import com.driveperf.pipeline.QualityAnalyticsFinaliser
import com.driveperf.pipeline.ScoreAnalyticsFinaliser
import com.driveperf.pipeline.capture.CapturePersistence
import com.driveperf.pipeline.capture.CaptureConfig
import com.driveperf.pipeline.capture.CapturePorts
import com.driveperf.pipeline.capture.TripFinaliser
import com.driveperf.pipeline.capture.TripRecorderEngine
import com.driveperf.pipeline.score.FeedbackRescorer
import com.driveperf.pipeline.score.HistoryRecompute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        finaliser: ((DrivePerfDatabase) -> TripFinaliser)? = null,
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
        finaliser: ((DrivePerfDatabase) -> TripFinaliser)?,
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
        val base = LocalTripRepository(db, files, io)
        val recompute = recompute(db, io, history, base, clock, previews)
        val internals = CoreInternals(
            db = db,
            io = io,
            files = files,
            previews = previews,
            history = history,
            analysisFiles = ProtoAnalysisFileStore(files, io),
            feedbackStore = LocalEventFeedbackStore(db, io, recompute),
            rescorer = FeedbackRescorer(history, previews),
            recompute = recompute,
        )
        val analytics = ConsentGatedAnalytics(platform.analyticsSink())
        val tripFinaliser = finaliser(finaliser?.invoke(db), internals, analytics)
        val recorder = recorder(platform, internals, store, tripFinaliser, clock, scope, capture, analytics)
        val trips = RecomputingTripRepository(base, db, files, io, clock, recompute)
        val routes = LocalRouteRepository(db, io)
        // DRIVE-100: trips corrected to not-a-drive more than 30 days ago are deleted once per start. The purge deletes
        // through the plain repository: not-drives are in no baseline, so there is nothing to recompute.
        val corrections = LocalTripCorrections(db, base, io, clock, recompute)
        scope.launch { corrections.purgeExpiredNotDrives() }
        return CoreServices(
            recorder = recorder,
            trips = trips,
            routes = routes,
            replay = TripReplayRepository(trips, internals.analysisFiles, previews),
            markEventNeeded = MarkEventNeeded(trips, routes, internals.feedbackStore, internals.rescorer, clock),
            corrections = corrections,
            analytics = analytics,
            settings = LocalSettingsRepository(db, io, clock, analytics).also { scope.followBatterySaver(it, recorder) },
            clock = clock,
            internals = internals,
            onClose = {
                scope.cancel()
                driver.close()
            },
        )
    }

    /**
     * DRIVE-131 (pipeline-and-scoring.md §5.7 "Corrections"): re-derives the scores of a trip and every later one after
     * feedback, a correction or a deletion. The feedback store, the corrections and `trips.delete` run it.
     */
    @Suppress("LongParameterList") // the stores the recompute reads and writes, gathered once in build
    private fun recompute(
        db: DrivePerfDatabase,
        io: CoroutineContext,
        history: TripHistory,
        trips: LocalTripRepository,
        clock: Clock,
        previews: ProtoPreviewStore,
    ): TripRecompute = HistoryRecompute(history, LocalScoreHistoryStore(db, io), trips, clock, previews)

    /**
     * The trip-end pipeline, [override] in tests and tools, with its analytics: data quality (F-DQ-02) and scoring
     * (`events_detected`, `baseline_established`).
     */
    private fun finaliser(override: TripFinaliser?, internals: CoreInternals, analytics: ConsentGatedAnalytics): TripFinaliser {
        val pipeline = override ?: PipelineFinaliser(
            internals.history, LocalRouteHistory(internals.db, internals.previews, internals.io),
        )
        return ScoreAnalyticsFinaliser(QualityAnalyticsFinaliser(pipeline, analytics), analytics, internals.history)
    }

    /** The capture state machine on the platform's sensors and the core's stores (ADR-002 section 5). */
    @Suppress("LongParameterList") // the recorder's ports, gathered once in build
    private fun recorder(
        platform: PlatformBindings,
        internals: CoreInternals,
        store: LocalTripStore,
        finaliser: TripFinaliser,
        clock: Clock,
        scope: CoroutineScope,
        capture: CaptureConfig,
        analytics: ConsentGatedAnalytics,
    ): TripRecorderEngine = TripRecorderEngine(
        ports = CapturePorts(
            location = platform.location(),
            motion = platform.motion(),
            activity = platform.activity(),
            power = platform.power(),
            parkedRegion = platform.parkedRegion(),
            files = internals.files,
            watchdog = platform.watchdog(),
            resumeNotice = platform.resumeNotice(),
            stopAlarm = platform.stopAlarm(),
        ),
        persistence = CapturePersistence(
            store,
            ProtoCaptureLog(internals.files, internals.io),
            finaliser,
            internals.previews,
            internals.analysisFiles,
            LocalWatchdogStore(internals.db, internals.io),
            LocalAutoDetectPauseStore(internals.db, internals.io),
        ),
        clock = clock,
        zones = SystemTimeZone(),
        scope = scope,
        config = capture,
        analytics = analytics,
    )

    /** S-SET-03: the recorder follows the saved battery-saver choice from start-up onwards. */
    private fun CoroutineScope.followBatterySaver(
        settings: com.driveperf.domain.settings.SettingsRepository,
        recorder: TripRecorderEngine,
    ) {
        recorder.setBatterySaverRecording(settings.state.value.batterySaverRecording)
        launch { settings.state.collect { recorder.setBatterySaverRecording(it.batterySaverRecording) } }
    }

    /** Forgets the started core. Tests only. */
    internal fun reset() {
        started = null
    }
}
