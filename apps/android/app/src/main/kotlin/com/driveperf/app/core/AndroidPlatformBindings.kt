package com.driveperf.app.core

import android.content.Context
import androidx.core.content.edit
import com.driveperf.di.PlatformBindings
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.foundation.newId

/** The `:apps:android:platform` adapters as the core's ports (ADR-002 section 5). */
@Suppress("LongParameterList") // one value per port
class AndroidPlatformBindings(
    private val clock: Clock,
    private val location: LocationSource,
    private val motion: MotionSource,
    private val activity: ActivitySource,
    private val power: PowerState,
    private val parkedRegion: ParkedRegionMonitor,
    private val files: TripFileStore,
    private val deviceId: String,
) : PlatformBindings {
    override fun clock(): Clock = clock

    override fun location(): LocationSource = location

    override fun motion(): MotionSource = motion

    override fun activity(): ActivitySource = activity

    override fun power(): PowerState = power

    override fun parkedRegion(): ParkedRegionMonitor = parkedRegion

    override fun tripFiles(): TripFileStore = files

    override fun deviceId(): String = deviceId
}

/**
 * This install's UUIDv7 device ID (data-model.md §1.4), created once and kept in app-private preferences in
 * credential-encrypted storage. Excluded from Android backup (`allowBackup = false`), so a new phone gets a new ID.
 */
object DeviceId {
    private const val PREFS = "driveperf_device"
    private const val KEY = "device_id"

    fun of(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: newId().also { prefs.edit(commit = true) { putString(KEY, it) } }
    }
}
