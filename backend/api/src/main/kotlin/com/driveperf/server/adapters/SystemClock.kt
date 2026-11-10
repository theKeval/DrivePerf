package com.driveperf.server.adapters

import com.driveperf.server.ports.Clock

internal class SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
