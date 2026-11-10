package com.driveperf.pipeline.signal

/** R0 → R1 thresholds of `pipe-1.0.0` (pipeline-and-scoring.md §2, §9 P-11…P-17). Immutable, compiled in (§0.6). */
public data class SignalConfig(
    val fs: Int = 25,
    // §2.3 GNSS gate (P-11)
    val maxHAccM: Double = 30.0,
    val maxSpeedMps: Double = 90.0,
    val maxAccelMps2: Double = 12.0,
    val teleportFactor: Double = 1.5,
    val teleportFloorMps: Double = 20.0,
    val continuityS: Double = 3.0,
    // §2.6 resampling (P-13)
    val interpolateMaxS: Double = 0.2,
    val lowRateHz: Int = 20,
    // §2.4 phone moved and stillness (P-17)
    val offAxisDps: Double = 45.0,
    val offAxisHoldS: Double = 0.25,
    val tiltDeg: Double = 20.0,
    val shockG: Double = 1.2,
    val shockGnssG: Double = 0.25,
    val hitMergeS: Double = 5.0,
    val spanBeforeS: Double = 2.0,
    val spanAfterS: Double = 3.0,
    val stillAccelStdG: Double = 0.008,
    val stillGyroDps: Double = 1.0,
    val stillWindowS: Double = 2.0,
    val mountedGravityStdDeg: Double = 3.0,
    // §2.5 alignment (P-16)
    val alignMinSpeedMps: Double = 3.0,
    val alignMaxCourseAccDeg: Double = 20.0,
    val alignMinExcitationG: Double = 0.03,
    val alignMinKept: Int = 20,
    val alignMinCoherence: Double = 0.6,
    val alignExcitationFloorG: Double = 0.05,
    val alignMaxBiasG: Double = 0.05,
    val alignMinEpochS: Double = 20.0,
    val alignSettleDeg: Double = 5.0,
    // §2.2 GNSS lag (P-15)
    val lagWindowS: Double = 300.0,
    val lagMaxMs: Int = 1500,
    val lagStepMs: Int = 40,
    val lagMinCorrelation: Double = 0.6,
    // §2.7 gaps (P-12)
    val gapLabelS: Double = 180.0,
    val tunnelMinS: Double = 5.0,
    val chordMin: Double = 0.5,
    val chordMax: Double = 1.5,
    // §2.8 estimated start (P-14)
    val estimateMaxM: Double = 5000.0,
    val estimateMaxMps: Double = 60.0,
    val estimateMaxS: Double = 900.0,
    val historyEdgeS: Double = 1.0,
    // §2.9 badge
    val unscorableShare: Double = 0.50,
    val limitedShare: Double = 0.70,
    val limitedGnss: Double = 0.60,
    val usableShare: Double = 0.85,
    val usableGnss: Double = 0.85,
    val usableImu: Double = 0.90,
    val minScoredS: Double = 120.0,
    // §3 metrics
    val stopMinS: Double = 10.0,
    val maxSpeedAccMps: Double = 2.0,
    val maxSpeedBandMps: Double = 2.0,
    val elevVAccM: Double = 15.0,
    val elevDeadBandM: Double = 5.0,
    // §1.6 short trips
    val shortTripM: Double = 500.0,
    val shortTripS: Double = 120.0,
)
