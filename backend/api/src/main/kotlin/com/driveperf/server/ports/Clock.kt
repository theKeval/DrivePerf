package com.driveperf.server.ports

/** Time source; adapters supply the real one, tests a fixed one. */
internal fun interface Clock {
    fun nowEpochMillis(): Long
}
