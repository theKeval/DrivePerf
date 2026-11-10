package com.driveperf.pipeline.capture

/**
 * Capture thresholds of `pipe-1.0.0` (pipeline-and-scoring.md §1, §2.7, §9 P-01…P-10). Immutable, compiled in: no
 * threshold can come from Remote Config (F-ANA-05 AC2). Values marked P-nn are provisional until A19.
 */
public data class CaptureConfig(
    // §1.5 confirm and reject (P-01…P-04)
    val goodFixHAccM: Double = 50.0,
    val confirmSpeedMps: Double = 6.94,
    val confirmHoldS: Double = 20.0,
    val activityConfirmSpeedMps: Double = 2.78,
    val activityConfirmFixAgeS: Double = 5.0,
    val rejectAfterS: Double = 90.0,
    val rejectMaxSpeedMps: Double = 4.17,
    val candidateTimeoutS: Double = 180.0,
    val activityHistoryS: Double = 900.0,
    // §1.6 stationary, resume, stop timers (P-06…P-08)
    val stationarySpeedMps: Double = 0.56,
    val stationaryFixes: Int = 3,
    val pauseAfterS: Double = 30.0,
    val resumeSpeedMps: Double = 2.0,
    val resumeFixes: Int = 3,
    val resumeImuMovingS: Double = 3.0,
    val autoStopS: Double = 240.0,
    val manualStopS: Double = 900.0,
    // §1.4 parked region (P-05)
    val parkedRadiusM: Double = 120.0,
    // §1.7 relaunch (P-09)
    val resumeAfterKillS: Double = 600.0,
    // §2.7 IMU rates and back-off (P-10)
    val imuNominalHz: Int = 50,
    val imuReducedHz: Int = 25,
    val imuIdleHz: Int = 10,
    val imuIdleAfterS: Double = 120.0,
    val imuWakeG: Double = 0.03,
    val lowBatteryPct: Int = 15,
    val endBatteryPct: Int = 5,
    val minFreeBytes: Long = 100L * 1024 * 1024,
    // §2.4 IMU stillness over 2 s
    val stillWindowS: Double = 2.0,
    val stillAccelStdG: Double = 0.008,
    val stillGyroDps: Double = 1.0,
    // Spike 1 changes 2 and 8: stale wakes and cached fixes
    val staleWakeS: Double = 300.0,
    val staleFixBeforeWakeS: Double = 30.0,
    // Live distance: gated fixes only, steps under 5 m ignored (Spike 1)
    val liveDistanceHAccM: Double = 30.0,
    val liveDistanceMinStepM: Double = 5.0,
    // R0 durability (ADR-003): fsync at most every 5 s
    val r0SyncS: Double = 5.0,
)
