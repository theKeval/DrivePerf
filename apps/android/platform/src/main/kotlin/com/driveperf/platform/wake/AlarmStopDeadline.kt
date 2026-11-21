package com.driveperf.platform.wake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driveperf.domain.capture.CaptureDeadlineAlarm
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

/**
 * The device wake behind the trip's stop timer (DRIVE-223). The core's timers are coroutine delays, which count on
 * `CLOCK_MONOTONIC` and stall while the CPU sleeps; with the screen off, GNSS idle and sensors batching, a parked trip
 * stayed open for hours and absorbed the next drive. This is an `ELAPSED_REALTIME_WAKEUP` alarm on the same clock as
 * the core (`elapsedRealtimeNanos`), set with `setAndAllowWhileIdle` so it also fires in Doze.
 *
 * No exact-alarm permission is needed. Android may deliver it up to about three quarters of the remaining wait late
 * (under 3 min for the 240 s stop timer), and in Doze at most about once per 9 min per app: one alarm per stop stays
 * well inside both. No wake lock is taken (F-CAP-16 AC3): the alarm's own wake lock covers the receiver, which holds
 * it with `goAsync` until the trip has ended, for a few seconds at most.
 *
 * The callback lives in this process only. When the process died meanwhile, the alarm just starts it, and the core's
 * launch recovers the trip (§1.7).
 */
class AlarmStopDeadline(private val context: Context) : CaptureDeadlineAlarm {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atMonoNs: Long, onDue: () -> Unit) {
        pending.set(onDue)
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atMonoNs / NS_PER_MS, alarmIntent(context))
    }

    override fun cancel() {
        pending.set(null)
        alarms.cancel(alarmIntent(context))
    }

    companion object {
        private const val REQUEST_CODE = 7204
        private const val NS_PER_MS = 1_000_000L
        private val pending = AtomicReference<(() -> Unit)?>(null)

        /** Runs the pending callback, once. False when none is pending (cancelled, or a new process). */
        internal fun fire(): Boolean {
            val onDue = pending.getAndSet(null) ?: return false
            onDue()
            return true
        }

        internal fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, StopDeadlineReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Delivers the stop deadline to the core and keeps the CPU awake (the alarm's wake lock, via `goAsync`) until the trip
 * has ended and committed, or for [HOLD_MS] at most, so it is not left half-finalised until the next wake.
 */
class StopDeadlineReceiver : BroadcastReceiver(), KoinComponent {
    private val recorder: TripRecorder by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (!AlarmStopDeadline.fire()) return
        val result = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(HOLD_MS) { recorder.status.first { it.state !in UNSETTLED } }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        /** Under the 10 s a receiver may run before Android reports it. */
        const val HOLD_MS = 9_000L
        val UNSETTLED = setOf(RecorderState.PAUSED, RecorderState.FINISHING)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
