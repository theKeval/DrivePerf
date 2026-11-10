package com.driveperf.foundation

/**
 * The only source of time for shared code (ADR-002 section 3, pipeline-and-scoring.md §0.1). Platform code supplies
 * it, so the monotonic reading uses the same clock as the samples it delivers: iOS converts every timestamp to that
 * clock, Android uses `elapsedRealtimeNanos` (pipeline-and-scoring.md §2.2). Tests use `TestClock` from
 * `:shared:testing`.
 */
public interface Clock {
    /** Monotonic time in nanoseconds. Never goes backwards and keeps counting while the phone sleeps. */
    public fun monotonicNs(): Long

    /** UTC wall-clock time in epoch milliseconds. For display and record stamps only, never for durations. */
    public fun wallMs(): Long
}
