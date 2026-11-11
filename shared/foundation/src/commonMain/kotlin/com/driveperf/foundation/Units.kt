package com.driveperf.foundation

/** Physical constants shared by every layer. Units are SI except acceleration, which the product speaks in g. */
public object Units {
    /** Standard gravity in m/s² (pipeline-and-scoring.md §0.4). */
    public const val G: Double = 9.80665

    /** Nanoseconds per second. */
    public const val NANOS_PER_SECOND: Long = 1_000_000_000L

    /** Nanoseconds per millisecond. */
    public const val NANOS_PER_MILLI: Long = 1_000_000L
}
