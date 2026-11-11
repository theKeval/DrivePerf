package com.driveperf.platform.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind

/**
 * Starts the capture foreground service for a wake: a manual Start from the UI, or an OS wake (activity transition,
 * parked-region exit) from a receiver, which Android lets start a location foreground service from the background.
 */
object CaptureStarter {
    fun start(context: Context, event: WakeEvent) {
        val intent = Intent(context, CaptureForegroundService::class.java)
            .setAction(CaptureForegroundService.ACTION_WAKE)
            .putExtra(EXTRA_KIND, event.kind.name)
            .putExtra(EXTRA_T_MONO_NS, event.tMonoNs)
            .putExtra(EXTRA_WALL_MS, event.wallMs)
            .putExtra(EXTRA_DETAIL, event.sourceDetail)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Keeps a trip the core resumed after a kill (§1.7) in a foreground service. */
    fun resume(context: Context) {
        ContextCompat.startForegroundService(
            context, Intent(context, CaptureForegroundService::class.java).setAction(CaptureForegroundService.ACTION_RESUME),
        )
    }

    fun stop(context: Context) {
        context.startService(Intent(context, CaptureForegroundService::class.java).setAction(CaptureForegroundService.ACTION_STOP))
    }

    /** The wake an intent from [start] carries, or null. */
    internal fun wakeOf(intent: Intent): WakeEvent? {
        val kind = WakeKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: return null
        val tMonoNs = intent.getLongExtra(EXTRA_T_MONO_NS, 0L)
        return WakeEvent(kind, tMonoNs, intent.getLongExtra(EXTRA_WALL_MS, 0L), intent.getStringExtra(EXTRA_DETAIL))
    }

    private const val EXTRA_KIND = "kind"
    private const val EXTRA_T_MONO_NS = "t_mono_ns"
    private const val EXTRA_WALL_MS = "wall_ms"
    private const val EXTRA_DETAIL = "detail"
}
