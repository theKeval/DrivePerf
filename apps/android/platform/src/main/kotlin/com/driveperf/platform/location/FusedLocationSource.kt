package com.driveperf.platform.location

import android.annotation.SuppressLint
import android.content.Context
import com.driveperf.platform.ports.CaptureFailure
import com.driveperf.platform.ports.Clock
import com.driveperf.platform.ports.LocationSample
import com.driveperf.platform.ports.LocationSource
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import java.util.concurrent.Executor

/**
 * [LocationSource] on the Fused Location Provider: high accuracy, one fix a second.
 *
 * Fixes are delivered on [callbackExecutor], never on the main thread. `getLastLocation` is deliberately not used:
 * it returns cached fixes, which is the stale-fix trap spike 1 hit. Stale and out-of-order fixes are dropped with
 * [FixFreshnessFilter]. Collection fails with [CaptureFailure.PermissionDenied] when fine location is not granted.
 */
class FusedLocationSource(
    context: Context,
    private val clock: Clock,
    private val callbackExecutor: Executor,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    override fun samples(): Flow<LocationSample> = flow {
        // One gate per collection, so a second collector never inherits the first one's ordering state.
        val gate = FreshnessGate(clock)
        emitAll(updates().filter(gate::accept))
    }

    @SuppressLint("MissingPermission") // Missing permission is caught below and surfaced as a typed failure.
    private fun updates(): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) trySend(location.toSample())
            }
        }
        try {
            client.requestLocationUpdates(request, callbackExecutor, callback)
        } catch (e: SecurityException) {
            close(CaptureFailure.PermissionDenied("ACCESS_FINE_LOCATION", e))
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private class FreshnessGate(private val clock: Clock) {
        private val filter = FixFreshnessFilter(notBeforeNs = clock.monotonicNs() - START_ALLOWANCE_NS)
        fun accept(sample: LocationSample): Boolean = filter.accept(sample.tMonoNs, clock.monotonicNs())
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 1_000L

        // A fix timestamped just before capture started is still part of the wake, so allow 2 s of slack.
        const val START_ALLOWANCE_NS = 2_000_000_000L
    }
}
