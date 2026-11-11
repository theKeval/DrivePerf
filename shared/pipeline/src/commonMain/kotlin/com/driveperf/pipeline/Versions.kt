package com.driveperf.pipeline

/** Algorithm IDs (pipeline-and-scoring.md §0.6). Any threshold change bumps one of them. */
public object Versions {
    /** Trip pipeline: capture rules, signal processing, detectors. */
    public const val PIPELINE: String = "pipe-1.0.0"

    /** Scoring: ledger, Smoothness, baseline, efficiency, tips. */
    public const val SCORING: String = "smooth-1.0.0"
}
