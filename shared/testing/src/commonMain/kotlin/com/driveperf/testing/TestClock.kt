package com.driveperf.testing

import com.driveperf.foundation.Clock
import com.driveperf.foundation.Units

/** A manual [Clock] for tests. Both readings move together when time advances. */
public class TestClock(
    private var monoNs: Long = 0L,
    private var wall: Long = DEFAULT_WALL_MS,
) : Clock {
    override fun monotonicNs(): Long = monoNs

    override fun wallMs(): Long = wall

    /** Moves both clocks forward by [ms] milliseconds. */
    public fun advanceMs(ms: Long) {
        monoNs += ms * Units.NANOS_PER_MILLI
        wall += ms
    }

    /** Moves both clocks forward by [s] seconds. */
    public fun advanceS(s: Double) {
        advanceMs((s * MS_PER_S).toLong())
    }

    public companion object {
        /** 2026-11-10T00:00:00Z: a fixed, arbitrary wall time so tests never depend on the date. */
        public const val DEFAULT_WALL_MS: Long = 1_794_268_800_000L
        private const val MS_PER_S = 1000.0
    }
}
