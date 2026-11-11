package com.driveperf.platform.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.driveperf.domain.capture.ActivityHistoryCallback
import com.driveperf.domain.capture.ActivitySink
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.foundation.DomainError
import com.driveperf.foundation.PermissionKind
import com.driveperf.platform.capture.CaptureFailureBus
import com.google.android.gms.location.ActivityRecognition

/**
 * [ActivitySource] on Activity Recognition updates (`requestActivityUpdates`), which carry a confidence for the most
 * probable activity. The core uses it as a hint beside GNSS and motion, never alone (PRD F-CAP-02). Only changes of
 * type or confidence level reach the sink. The transition API is kept for the background wake path, not used here.
 *
 * A missing `ACTIVITY_RECOGNITION` permission is reported on [failures]; the trip still records from location and
 * motion.
 */
class ActivityRecognitionSource(
    context: Context,
    private val failures: CaptureFailureBus,
    private val detectionIntervalMs: Long = DEFAULT_INTERVAL_MS,
) : ActivitySource {
    private val appContext = context.applicationContext
    private val client = ActivityRecognition.getClient(appContext)
    private val history = ActivityHistory()
    private var pendingIntent: PendingIntent? = null

    @Synchronized
    override fun start(sink: ActivitySink) {
        stop()
        val intent = pendingIntent()
        val changes = ActivityChangeFilter()
        ActivityUpdateHub.attach { reading ->
            changes.onReading(reading)?.let { change ->
                history.add(change)
                sink.onActivity(listOf(change))
            }
        }
        if (!request(intent)) ActivityUpdateHub.detach() else pendingIntent = intent
    }

    @Synchronized
    override fun stop() {
        ActivityUpdateHub.detach()
        pendingIntent?.let(::remove)
        pendingIntent = null
    }

    override fun queryHistory(fromWallMs: Long, toWallMs: Long, callback: ActivityHistoryCallback) {
        callback.onResult(history.between(fromWallMs, toWallMs))
    }

    @SuppressLint("MissingPermission") // A revoked permission means there is nothing left to remove.
    private fun remove(intent: PendingIntent) {
        try {
            client.removeActivityUpdates(intent)
        } catch (_: SecurityException) {
            // The registration died with the permission.
        }
        intent.cancel()
    }

    @SuppressLint("MissingPermission") // Missing permission is caught below and reported as a typed failure.
    private fun request(intent: PendingIntent): Boolean = try {
        client.requestActivityUpdates(detectionIntervalMs, intent)
        true
    } catch (_: SecurityException) {
        failures.report(DomainError.Capture.PermissionDenied(PermissionKind.MOTION))
        false
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        Intent(appContext, ActivityUpdateReceiver::class.java)
            .setComponent(ComponentName(appContext, ActivityUpdateReceiver::class.java)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // the OS fills in the result extras
    )

    private companion object {
        const val REQUEST_CODE = 7201
        const val DEFAULT_INTERVAL_MS = 5_000L
    }
}
