package com.driveperf.platform.activity

import android.content.Intent
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.foundation.Clock
import com.google.android.gms.location.ActivityRecognitionResult
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Hand-over from the manifest-declared [ActivityUpdateReceiver] (which the OS creates, so it cannot be given
 * constructor arguments) to the running [ActivityRecognitionSource]. There is at most one listener: the source that
 * is started.
 */
internal object ActivityUpdateHub {
    private val listener = AtomicReference<((ActivitySample) -> Unit)?>(null)

    fun attach(onReading: (ActivitySample) -> Unit) = listener.set(onReading)

    fun detach() = listener.set(null)

    /** Converts the OS result to a reading and passes it on. Results older than [MAX_AGE_NS] are dropped as cached. */
    fun deliver(intent: Intent, clock: Clock) {
        val target = listener.get()
        val reading = if (target == null) null else readingOf(intent, clock)
        if (target != null && reading != null) target(reading)
    }

    private fun readingOf(intent: Intent, clock: Clock): ActivitySample? {
        val result = (if (ActivityRecognitionResult.hasResult(intent)) ActivityRecognitionResult.extractResult(intent) else null)
            ?: return null
        val best = result.mostProbableActivity
        val tMonoNs = TimeUnit.MILLISECONDS.toNanos(result.elapsedRealtimeMillis)
        val cached = clock.monotonicNs() - tMonoNs > MAX_AGE_NS
        return if (cached) null else ActivitySample(tMonoNs, result.time, activityTypeOf(best.type), confidenceOf(best.confidence))
    }

    private val MAX_AGE_NS = TimeUnit.SECONDS.toNanos(30)
}
