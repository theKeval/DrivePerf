package com.driveperf.app

import android.app.Application
import com.driveperf.app.core.WakePath
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.platform.capture.CaptureNotifications
import com.driveperf.platform.crash.PrivacyChoices
import com.driveperf.platform.platformModule
import kotlinx.coroutines.CoroutineScope
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
            modules(platformModule, appModule)
        }.koin
        koin.get<CaptureNotifications>().ensureChannel()
        // ADR-007: Sentry starts only if the user opted in to crash reports, and usage statistics are applied only once
        // the user has chosen (events wait in memory until then); otherwise nothing is initialised.
        koin.get<PrivacyChoices>().restore()
        // Every process start, including one caused by a wake or a reboot: start the shared core, re-arm the wake path,
        // then let the core recover a killed trip and re-arm the parked region (ADR-002 section 5, §1.7).
        koin.get<WakePath>().install(koin.get<PermissionReader>().read(), koin.get<CoroutineScope>())
        koin.get<TripRecorder>().onLaunch()
    }
}
