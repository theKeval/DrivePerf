package com.driveperf.platform

import android.os.SystemClock
import com.driveperf.foundation.Clock

/** [Clock] backed by `elapsedRealtimeNanos`, the same time base GNSS fixes and sensor events use. */
object AndroidClock : Clock {
    override fun monotonicNs(): Long = SystemClock.elapsedRealtimeNanos()
    override fun wallMs(): Long = System.currentTimeMillis()
}
