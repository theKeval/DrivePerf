package com.driveperf.app

import android.app.Application
import com.driveperf.platform.capture.CaptureNotifications
import com.driveperf.platform.crash.ConsentStore
import com.driveperf.platform.crash.CrashReporter
import com.driveperf.platform.platformModule
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
        // ADR-007: Sentry starts only if the user opted in to crash reports; otherwise nothing is initialised.
        koin.get<CrashReporter>().setEnabled(koin.get<ConsentStore>().crashReportsAllowed)
    }
}
