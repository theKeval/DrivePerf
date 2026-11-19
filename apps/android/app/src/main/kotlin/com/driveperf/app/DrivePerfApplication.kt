package com.driveperf.app

import android.app.Application
import com.driveperf.app.core.WakePath
import com.driveperf.app.di.analysisModule
import com.driveperf.app.di.drivesModule
import com.driveperf.app.di.replayModule
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.platform.capture.AutoTripNoticeTaps
import com.driveperf.platform.capture.CaptureNotifications
import com.driveperf.platform.capture.DriveDecodedNotice
import com.driveperf.platform.crash.CrashReporter
import com.driveperf.platform.crash.PrivacyChoices
import com.driveperf.platform.platformModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DrivePerfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@DrivePerfApplication)
            modules(platformModule, appModule, drivesModule, replayModule, analysisModule)
        }.koin
        koin.get<CaptureNotifications>().ensureChannel()
        // ADR-007: Sentry starts only if the user opted in to crash reports, and usage statistics are applied only once
        // the user has chosen (events wait in memory until then); otherwise nothing is initialised.
        koin.get<PrivacyChoices>().restore()
        // Every process start, including one caused by a wake or a reboot: start the shared core, re-arm the wake path,
        // then let the core recover a killed trip and re-arm the parked region (ADR-002 section 5, §1.7).
        koin.get<WakePath>().install(
            permissions = koin.get<PermissionReader>().read(),
            scope = koin.get<CoroutineScope>(),
            location = koin.get(),
        )
        val recorder = koin.get<TripRecorder>()
        recorder.onLaunch()
        // DRIVE-137, after the core has started: the "Drive decoded." channel, and the tap counter for the automatic
        // trip's notice (auto_trip_notification_tapped), registered before any activity is created.
        koin.get<DriveDecodedNotice>().ensureChannel()
        registerActivityLifecycleCallbacks(koin.get<AutoTripNoticeTaps>())
        // DRIVE-196: crash reports say whether a trip was being captured (in_trip, capture_mode, trip_state).
        val crashReporter = koin.get<CrashReporter>()
        recorder.status.onEach(crashReporter::setTrip).launchIn(koin.get<CoroutineScope>())
    }
}
