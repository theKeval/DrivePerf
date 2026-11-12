package com.driveperf.domain.capture

// Device-facing ports (ADR-002 section 3). Platform code implements them: iOS in DPPlatform (Swift), Android in
// :apps:android:platform. Ports implemented in Swift must not use `suspend` or `Flow`, so these are callback style.
// The core calls them from one confined thread and expects callbacks on any thread: it serialises them itself.
// Adapters convert units and signs (pipeline-and-scoring.md §2.1) and contain no business logic: they never decide
// whether a trip exists, how a sample is filtered or what a score is.

/** Receives GNSS fixes. Batches of about 1 s are fine (iOS delivers to Kotlin in ≈ 1 s batches, §0.1). */
public fun interface LocationSink {
    public fun onLocations(samples: List<LocationSample>)
}

/** Receives device-motion samples, in time order within a batch. */
public fun interface MotionSink {
    public fun onMotion(samples: List<MotionSample>)
}

/** Receives live activity changes. */
public fun interface ActivitySink {
    public fun onActivity(samples: List<ActivitySample>)
}

/** Receives power changes. */
public fun interface PowerSink {
    public fun onPower(status: PowerStatus)
}

/** Called once with the result of a history query; an empty list when there is none or it is unsupported. */
public fun interface ActivityHistoryCallback {
    public fun onResult(samples: List<ActivitySample>)
}

/** Called once with recorded accelerometer history; an empty list when unsupported (Android) or unavailable. */
public fun interface RecordedAccelCallback {
    public fun onResult(samples: List<RecordedAccelSample>)
}

/**
 * 1 Hz GNSS at best accuracy for automotive use (iOS `liveUpdates(.automotiveNavigation)`, Android Fused Location
 * high accuracy). Running it is what keeps a backgrounded app alive while recording.
 */
public interface LocationSource {
    /** Starts delivering fixes to [sink]. Calling it again replaces the sink. */
    public fun start(sink: LocationSink)

    /** Stops fixes. Safe to call when stopped. */
    public fun stop()
}

/** Device motion (iOS `CMDeviceMotion`; Android linear acceleration, gravity, gyroscope, game rotation vector). */
public interface MotionSource {
    /** What the hardware supports. */
    public fun capability(): MotionCapability

    /** Starts delivering samples at [rateHz] (nominal 50) to [sink]. */
    public fun start(rateHz: Int, sink: MotionSink)

    /** Changes the rate while running (F-CAP-16 back-off: 50, 25 or 10 Hz). */
    public fun setRate(rateHz: Int)

    /** Stops samples. Safe to call when stopped. */
    public fun stop()

    /**
     * Raw accelerometer history between two UTC times, for the estimated start (§2.8). iOS reads `CMSensorRecorder`;
     * Android answers with an empty list. [callback] runs exactly once.
     */
    public fun queryRecordedAccelerometer(fromWallMs: Long, toWallMs: Long, callback: RecordedAccelCallback)
}

/** The OS activity classifier (iOS `CMMotionActivityManager`, Android Activity Recognition Transition API). */
public interface ActivitySource {
    /** Starts delivering live activity changes to [sink]. */
    public fun start(sink: ActivitySink)

    /** Stops live updates. Safe to call when stopped. */
    public fun stop()

    /**
     * Activity history between two UTC times, oldest first. iOS queries `CMMotionActivityManager`; Android answers
     * from the transitions it has persisted. [callback] runs exactly once. The core bounds the window itself
     * (Spike 1 change 3), so adapters must return the window as asked and never pad it.
     */
    public fun queryHistory(fromWallMs: Long, toWallMs: Long, callback: ActivityHistoryCallback)
}

/** Battery, Low Power Mode and thermal state. */
public interface PowerState {
    /** The current status, read synchronously. */
    public fun current(): PowerStatus

    /** Starts delivering changes to [sink]. */
    public fun start(sink: PowerSink)

    /** Stops updates. */
    public fun stop()
}

/**
 * The region around the last parking spot whose exit wakes the app (`WakeKind.PARKED_EXIT`): iOS `CLMonitor` with an
 * alphanumeric name, Android Geofencing API. Spike 1 found it the trigger that matters; the core re-arms it at every
 * trip end and on every launch.
 */
public interface ParkedRegionMonitor {
    /** Arms (or moves) the single parked region. */
    public fun arm(center: GeoPoint, radiusM: Double)

    /** Removes it. */
    public fun disarm()
}

/**
 * The auto-detect watchdog notification (F-CAP-14 AC1, DTS pattern, A3 §1.5): a local notification that fires only if
 * the app is not woken again in time, saying "Auto-detect may be paused. Open DrivePerf to keep it on." The core
 * decides when (24 h after the last check-in, at most once per 72 h, never while auto-detect is off); the adapter only
 * schedules what it is told. iOS uses `UNUserNotificationCenter` with one fixed request identifier, so scheduling again
 * replaces the pending request. Android needs none (force-stop and boot are handled at launch) and uses
 * [NoOpAutoDetectWatchdog].
 */
public interface AutoDetectWatchdog {
    /** Schedules the notification at [fireAtWallMs] (UTC), replacing any pending one. A past time fires at once. */
    public fun schedule(fireAtWallMs: Long)

    /** Removes the pending notification, if any. Safe to call when none is pending. */
    public fun cancel()
}

/** A watchdog that schedules nothing: Android, tests and builds without notifications. */
public object NoOpAutoDetectWatchdog : AutoDetectWatchdog {
    override fun schedule(fireAtWallMs: Long): Unit = Unit

    override fun cancel(): Unit = Unit
}
