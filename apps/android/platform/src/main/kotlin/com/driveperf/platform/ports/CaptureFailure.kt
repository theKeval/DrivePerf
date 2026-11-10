package com.driveperf.platform.ports

/** Typed capture failures, so callers never branch on exception text (ADR-002 section 7: `Capture` errors). */
sealed class CaptureFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(val permission: String, cause: Throwable? = null) :
        CaptureFailure("Permission denied: $permission", cause)

    class SensorUnavailable(val sensor: String) : CaptureFailure("Sensor unavailable: $sensor")
}
