package com.driveperf.platform.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.driveperf.domain.capture.LocationSink
import com.driveperf.domain.capture.LocationSource
import com.driveperf.foundation.Clock
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.PermissionKind
import com.driveperf.platform.capture.CaptureFailureBus
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.Executor

/**
 * [LocationSource] on the Fused Location Provider: high accuracy, one fix a second.
 *
 * Fixes reach the sink on [callbackExecutor], never on the main thread. `getLastLocation` is deliberately not used: it
 * returns cached fixes, which is the stale-fix trap spike 1 hit. Stale and out-of-order fixes are dropped before the
 * sink sees them ([LocationBatchFilter]). A missing fine-location permission is reported on [failures] and the source
 * stays stopped.
 */
class FusedLocationSource(
    context: Context,
    private val clock: Clock,
    private val callbackExecutor: Executor,
    private val failures: CaptureFailureBus,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private var active: Session? = null
    private var batchingEnabled = false

    @Synchronized
    override fun start(sink: LocationSink) {
        val running = active
        if (running != null) {
            running.sink = sink // "Calling it again replaces the sink."
            return
        }
        val session = Session(sink, LocationBatchFilter(clock))
        if (register(session)) active = session
    }

    @Synchronized
    override fun stop() {
        active?.let { client.removeLocationUpdates(it.callback) }
        active = null
        batchingEnabled = false
    }

    @Synchronized
    fun setBatchingEnabled(enabled: Boolean) {
        val session = active ?: return
        if (batchingEnabled == enabled) return
        batchingEnabled = enabled
        if (enabled) {
            register(session)
        } else {
            // Deliver buffered fixes before replacing the request on the same callback.
            client.flushLocations().addOnCompleteListener(callbackExecutor) {
                synchronized(this) {
                    if (active === session && !batchingEnabled) register(session)
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Missing permission is caught below and reported as a typed failure.
    private fun register(session: Session): Boolean {
        val request = captureLocationRequest(intervalMs, batchingEnabled)
        return try {
            client.requestLocationUpdates(request, callbackExecutor, session.callback)
            true
        } catch (_: SecurityException) {
            failures.report(DomainError.Capture.PermissionDenied(PermissionKind.LOCATION))
            false
        }
    }

    /** One start-to-stop run: its sink (replaceable), its filter and the provider callback that feeds them. */
    private class Session(@Volatile var sink: LocationSink, private val filter: LocationBatchFilter) {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val now = SystemClock.elapsedRealtimeNanos()
                val fresh = filter.fresh(result.locations.map { it.toSample(now) })
                if (fresh.isNotEmpty()) sink.onLocations(fresh)
            }
        }
    }
}

private const val DEFAULT_INTERVAL_MS = 1_000L
private const val BATCH_DELAY_MS = 3_000L

/** Sample at the same rate in both modes. A short delay keeps the batch inside the freshness window. */
internal fun captureLocationRequest(intervalMs: Long = DEFAULT_INTERVAL_MS, batched: Boolean): LocationRequest =
    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
        .setMinUpdateIntervalMillis(intervalMs)
        .setWaitForAccurateLocation(false)
        .setMaxUpdateDelayMillis(if (batched) BATCH_DELAY_MS else 0L)
        .build()
