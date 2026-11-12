package com.driveperf.pipeline.capture

import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.WakeKind

/**
 * Capture analytics (PRD F-CAP-02, F-CAP-03, F-CAP-05, F-CAP-14, F-CAP-16; ADR-007). Names and values are fixed keys or
 * buckets: no coordinates, speeds, times or free text. The reducer emits them as [MachineEffect.Track], so the rules
 * that decide them stay in one place; the engine sends them through the consent-gated `Analytics` port.
 */
internal data class CaptureEvent(override val name: String, override val properties: Map<String, String> = emptyMap()) :
    AnalyticsEvent {
    companion object {
        /** `auto_wake {trigger}`: an OS trigger started a candidate. */
        fun autoWake(kind: WakeKind): CaptureEvent = CaptureEvent("auto_wake", mapOf("trigger" to kind.key()))

        /** `auto_candidate {result: confirmed, reason: speed|activity}`. */
        fun confirmed(byActivity: Boolean): CaptureEvent =
            CaptureEvent("auto_candidate", mapOf("result" to "confirmed", "reason" to if (byActivity) "activity" else "speed"))

        /** `auto_candidate {result: rejected, reason}`; [reason] is a [RejectReason] key. */
        fun rejected(reason: RejectReason): CaptureEvent =
            CaptureEvent("auto_candidate", mapOf("result" to "rejected", "reason" to reason.key))

        /** `trip_started {mode: auto, trigger}` when a candidate is confirmed. */
        fun autoStarted(kind: WakeKind): CaptureEvent =
            CaptureEvent("trip_started", mapOf("mode" to "auto", "trigger" to kind.key()))

        /** `trip_started {mode: manual, trigger: in_app|control|widget|tile}` from the wake's source detail. */
        fun manualStarted(sourceDetail: String?): CaptureEvent =
            CaptureEvent("trip_started", mapOf("mode" to "manual", "trigger" to manualTrigger(sourceDetail)))

        /** `trip_ended {by}`. */
        fun ended(by: String): CaptureEvent = CaptureEvent("trip_ended", mapOf("by" to by))

        /** `trip_finalise_ms {bucket}`. */
        fun finaliseMs(ms: Long): CaptureEvent = CaptureEvent("trip_finalise_ms", mapOf("bucket" to finaliseBucket(ms)))

        /** `rearmed {reason: boot|force_stop|launch}`. */
        fun rearmed(reason: RearmReason): CaptureEvent = CaptureEvent("rearmed", mapOf("reason" to reason.name.lowercase()))

        val watchdogFired: CaptureEvent = CaptureEvent("watchdog_fired")
        val watchdogOpened: CaptureEvent = CaptureEvent("watchdog_opened")
    }
}

private fun WakeKind.key(): String = name.lowercase()

/** The live-surface source keys of [com.driveperf.domain.capture.WakeEvent.sourceDetail] as the PRD names them. */
private fun manualTrigger(detail: String?): String = when (detail) {
    "control_center", "live_activity" -> "control"
    "widget" -> "widget"
    "qs_tile" -> "tile"
    else -> "in_app"
}

private const val FINALISE_1S = 1_000L
private const val FINALISE_3S = 3_000L
private const val FINALISE_10S = 10_000L
private const val FINALISE_30S = 30_000L

private fun finaliseBucket(ms: Long): String = when {
    ms < FINALISE_1S -> "lt_1s"
    ms < FINALISE_3S -> "1_3s"
    ms < FINALISE_10S -> "3_10s"
    ms < FINALISE_30S -> "10_30s"
    else -> "gte_30s"
}

/**
 * Why a candidate was dropped (`auto_candidate` reason). [TIMEOUT] is the PRD's `speed`: 3 min passed without the
 * speed or activity rule confirming a drive.
 */
internal enum class RejectReason(val key: String) {
    WALKING("walking"),
    RUNNING("running"),
    CYCLING("cycling"),
    TIMEOUT("speed"),
    STORAGE("storage"),
}
