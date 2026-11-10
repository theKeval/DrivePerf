package com.driveperf.app

import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.bridge.PlatformTripListSource
import com.driveperf.feature.capture.CaptureController
import com.driveperf.feature.capture.CaptureViewModel
import com.driveperf.feature.drives.DrivesViewModel
import com.driveperf.feature.drives.TripListSource
import com.driveperf.feature.onboarding.OnboardingViewModel
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.platform.crash.ConsentStore
import com.driveperf.platform.crash.CrashReporter
import com.driveperf.platform.crash.SentryCrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** App-level bindings: bridges from feature interfaces to platform adapters, consent, crash reporting, view models. */
val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ConsentStore(androidContext()) }
    single { PermissionReader(androidContext()) }
    single<CrashReporter> { SentryCrashReporter(androidContext(), BuildConfig.SENTRY_DSN, BuildConfig.VERSION_NAME) }

    single { PlatformCaptureController(androidContext(), get(), get(), get()) } bind CaptureController::class
    single<TripListSource> { PlatformTripListSource(get()) }

    viewModel { CaptureViewModel(get(), System::currentTimeMillis) }
    viewModel { DrivesViewModel(get()) }
    viewModel {
        val consent = get<ConsentStore>()
        OnboardingViewModel(get<PermissionReader>().read(), consent.crashReportsAllowed, consent.usageStatisticsAllowed)
    }
}
