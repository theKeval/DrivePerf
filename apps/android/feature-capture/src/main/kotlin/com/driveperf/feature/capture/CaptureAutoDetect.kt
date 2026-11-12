package com.driveperf.feature.capture

import androidx.annotation.StringRes

class CaptureAutoDetect private constructor(@StringRes internal val label: Int, internal val lastCheckInMs: Long?) {
    companion object {
        fun off() = CaptureAutoDetect(R.string.capture_auto_detect_off, null)
        fun on(lastCheckInMs: Long?) = CaptureAutoDetect(R.string.capture_auto_detect_on, lastCheckInMs)
        fun needsAttention(lastCheckInMs: Long?) = CaptureAutoDetect(R.string.capture_auto_detect_attention, lastCheckInMs)
    }
}
