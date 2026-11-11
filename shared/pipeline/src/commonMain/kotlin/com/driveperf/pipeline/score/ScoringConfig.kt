package com.driveperf.pipeline.score

/**
 * Thresholds of `smooth-1.0.0` and the R1 stages of `pipe-1.0.0` (pipeline-and-scoring.md §3–§5, §9 P-18…P-23).
 * Immutable and compiled in (§0.6). Any change bumps a version and regenerates the §8.3 goldens.
 */
public data class ScoringConfig(
    val fs: Int = 25,
    // §3 moving mask
    val movingOnMps: Double = 1.0,
    val movingOffMps: Double = 0.5,
    // §4.1 detectors
    val brakeEnterG: Double = 0.30,
    val brakeExitG: Double = 0.20,
    val brakeMinS: Double = 0.40,
    val accelEnterG: Double = 0.28,
    val accelExitG: Double = 0.18,
    val accelMinS: Double = 0.40,
    val cornerEnterG: Double = 0.36,
    val cornerExitG: Double = 0.25,
    val cornerMinS: Double = 0.50,
    val eventSpeedGateMps: Double = 2.5,
    val cornerYawGateDps: Double = 5.0,
    val eventMergeS: Double = 1.0,
    val maxEvents: Int = 500,
    // §4.3 road impact
    val impactVertG: Double = 0.40,
    val impactHalfWidth: Int = 15,
    val impactMaxS: Double = 0.8,
    val impactRatio: Double = 1.5,
    val impactMargin: Int = 5,
    // §4.4 confidence
    val confidenceHigh: Double = 0.20,
    val confidenceMedium: Double = 0.07,
    // §5.2 ledger
    val bandMinMps3: Double = 1.2,
    val bandMaxMps3: Double = 2.4,
    val bandSigmaMultiplier: Double = 3.0,
    val noiseQuietG: Double = 0.08,
    val noiseMinSamples: Int = 750,
    val reliefBefore: Int = 25,
    val reliefAfter: Int = 13,
    val reliefMinStopS: Double = 1.0,
    // §5.3 event cost
    val eventCostBase: Double = 2.0,
    val weightHigh: Double = 1.0,
    val weightMedium: Double = 0.7,
    val weightLow: Double = 0.4,
    // §5.4 ledger windows
    val eventWindow: Int = 25,
    // §5.5 context
    val fastMps: Double = 22.2,
    val mixedFrom: Double = 0.15,
    val highwayFrom: Double = 0.60,
    val cityFactor: Double = 1.00,
    val mixedFactor: Double = 0.85,
    val highwayFactor: Double = 0.70,
    // §5.6 exposure
    val minExposureS: Double = 120.0,
)
