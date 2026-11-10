package com.driveperf.platform.ports

import android.os.SystemClock

/** [Clock] backed by `elapsedRealtimeNanos`, the same time base GNSS fixes and sensor events use. */
object AndroidClock : Clock {
    override fun monotonicNs(): Long = SystemClock.elapsedRealtimeNanos()
    override fun wallMs(): Long = System.currentTimeMillis()
}
