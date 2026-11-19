package com.driveperf.platform

import com.driveperf.domain.settings.SettingsRepository
import java.util.Locale

import android.os.UserManager
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.platform.activity.ActivityRecognitionSource
import com.driveperf.platform.capture.AutoTripNoticeTaps
import com.driveperf.platform.capture.AutoTripNoticeTracker
import com.driveperf.platform.capture.CaptureFailureBus
import com.driveperf.platform.capture.DriveDecodedNotice
import com.driveperf.platform.capture.CaptureNotifications
import com.driveperf.platform.capture.FileTripStore
import com.driveperf.platform.location.FixWatch
import com.driveperf.platform.location.FusedLocationSource
import com.driveperf.platform.motion.SensorMotionSource
import com.driveperf.platform.power.BatteryPowerState
import com.driveperf.platform.wake.ActivityTransitions
import com.driveperf.platform.wake.GeofenceParkedRegionMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors

/**
 * Koin bindings for every Android adapter. The app's composition root includes this module and hands the ports to
 * `DrivePerfCore.start` as `PlatformBindings` (ADR-002 section 5). The location port is wrapped in [FixWatch] so the
 * UI can show the GNSS state.
 */
val platformModule = module {
    single<Clock> { AndroidClock }
    single { CaptureFailureBus() }
    single { FixWatch(FusedLocationSource(androidContext(), get(), Executors.newSingleThreadExecutor(), get()), get()) } bind
        LocationSource::class
    single<MotionSource> { SensorMotionSource(androidContext(), get(), get()) }
    single<ActivitySource> { ActivityRecognitionSource(androidContext(), get()) }
    single<PowerState> { BatteryPowerState(androidContext(), Executors.newSingleThreadExecutor()) }
    single { GeofenceParkedRegionMonitor(androidContext()) } bind ParkedRegionMonitor::class
    single { ActivityTransitions(androidContext()) }
    single<TripFileStore> {
        val users = androidContext().getSystemService(UserManager::class.java)
        FileTripStore(File(androidContext().filesDir, "trips"), isUnlocked = users::isUserUnlocked)
    }
    // DRIVE-137: the automatic trip's notice analytics and its "Drive decoded." replacement. Analytics and the trip
    // repository come from the shared core in the app's module.
    single { AutoTripNoticeTracker(get()) }
    single { AutoTripNoticeTaps(get()) }
    single { DriveDecodedNotice(androidContext(), get()) }
    single {
        CaptureNotifications(androidContext()) {
            get<SettingsRepository>().state.value.effectiveUnits(Locale.getDefault().country)
        }
    }
}
