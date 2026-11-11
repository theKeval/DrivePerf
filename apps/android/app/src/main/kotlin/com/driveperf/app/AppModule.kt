package com.driveperf.app

import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.bridge.PlatformTripListSource
import com.driveperf.app.core.AndroidPlatformBindings
import com.driveperf.app.core.DeviceId
import com.driveperf.app.core.WakePath
import com.driveperf.di.CoreServices
import com.driveperf.di.DrivePerfCore
import com.driveperf.di.PlatformBindings
import com.driveperf.di.start
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.repository.TripRepository
import com.driveperf.feature.capture.CaptureController
import com.driveperf.feature.capture.CaptureViewModel
import com.driveperf.feature.drives.DrivesViewModel
import com.driveperf.feature.drives.TripListSource
import com.driveperf.feature.onboarding.OnboardingViewModel
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.platform.crash.ConsentStore
import com.driveperf.platform.crash.CrashReporter
import com.driveperf.platform.crash.SentryCrashReporter
import com.driveperf.platform.location.FixWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * App-level bindings: the shared core on the platform adapters, bridges from feature interfaces to it, consent, crash
 * reporting, view models.
 */
val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ConsentStore(androidContext()) }
    single { PermissionReader(androidContext()) }
    single<CrashReporter> { SentryCrashReporter(androidContext(), BuildConfig.SENTRY_DSN, BuildConfig.VERSION_NAME) }

    single<PlatformBindings> {
        AndroidPlatformBindings(
            get(), get<FixWatch>(), get(), get(), get(), get<ParkedRegionMonitor>(), get(), DeviceId.of(androidContext()),
        )
    }
    single<CoreServices> { DrivePerfCore.start(get(), androidContext()) }
    single<TripRecorder> { get<CoreServices>().recorder }
    single<TripRepository> { get<CoreServices>().trips }
    single { WakePath(androidContext(), get(), get(), get()) }

    single {
        PlatformCaptureController(androidContext(), get(), get(), get(), get(), get(), get<WakePath>()::refresh)
    } bind CaptureController::class
    single<TripListSource> { PlatformTripListSource(get()) }

    viewModel { CaptureViewModel(get(), System::currentTimeMillis) }
    viewModel { DrivesViewModel(get()) }
    viewModel {
        val consent = get<ConsentStore>()
        OnboardingViewModel(get<PermissionReader>().read(), consent.crashReportsAllowed, consent.usageStatisticsAllowed)
    }
}
