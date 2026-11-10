package com.driveperf.platform.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops the capture foreground service. Call from the foreground UI. */
object CaptureStarter {
    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, CaptureForegroundService::class.java))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, CaptureForegroundService::class.java).setAction(CaptureForegroundService.ACTION_STOP))
    }
}
