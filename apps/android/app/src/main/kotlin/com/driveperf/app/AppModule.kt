package com.driveperf.app

import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.bridge.PlatformTripListSource
import com.driveperf.app.core.AndroidPlatformBindings
import com.driveperf.app.core.ConsentOnboardingSettings
import com.driveperf.app.core.DeviceId
import com.driveperf.app.core.WakePath
import com.driveperf.di.CoreServices
import com.driveperf.di.DrivePerfCore
import com.driveperf.di.PlatformBindings
import com.driveperf.di.start
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.TripRecorder
import com.driveperf.domain.repository.TripRepository
import com.driveperf.feature.capture.CaptureController
import com.driveperf.feature.capture.CaptureViewModel
import com.driveperf.feature.drives.DrivesViewModel
import com.driveperf.feature.drives.TripCorrector
import com.driveperf.feature.drives.TripListSource
import com.driveperf.feature.onboarding.ConsentChoices
import com.driveperf.feature.onboarding.OnboardingSettings
import com.driveperf.feature.onboarding.OnboardingViewModel
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.feature.onboarding.currentOemGuide
import com.driveperf.platform.analytics.StubAnalyticsSink
import com.driveperf.platform.crash.ConsentSettings
import com.driveperf.platform.crash.ConsentStore
import com.driveperf.platform.crash.CrashReporter
import com.driveperf.platform.crash.PrivacyChoices
import com.driveperf.platform.crash.SentryCrashReporter
import com.driveperf.platform.location.FixWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * App-level bindings: the shared core on the platform adapters, bridges from feature interfaces to it, consent, crash
 * reporting, view models.
 */
val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ConsentStore(androidContext()) } bind ConsentSettings::class
    single { PermissionReader(androidContext()) }
    single<CrashReporter> { SentryCrashReporter(androidContext(), BuildConfig.SENTRY_DSN, BuildConfig.VERSION_NAME) }

    // DRIVE-70: analytics port. No Firebase project yet (DRIVE-42), so the sink is a stub until it exists.
    single<AnalyticsSink> { StubAnalyticsSink() }

    single<PlatformBindings> {
        AndroidPlatformBindings(
            get(), get<FixWatch>(), get(), get(), get(), get<ParkedRegionMonitor>(), get(), DeviceId.of(androidContext()), get(),
        )
    }
    single<CoreServices> { DrivePerfCore.start(get(), androidContext()) }
    single<TripRecorder> { get<CoreServices>().recorder }
    single<Analytics> { get<CoreServices>().analytics }
    single { PrivacyChoices(get(), get(), get()) }
    single<TripRepository> { get<CoreServices>().trips }
    single { WakePath(androidContext(), get(), get(), get()) { get<ConsentSettings>().automaticTripsEnabled } }

    single {
        PlatformCaptureController(androidContext(), get(), get(), get(), get(), get(), get(), get(), get<WakePath>()::refresh)
    } bind CaptureController::class
    // DRIVE-100 (S2): the trip list also answers and corrects what a trip was.
    single { PlatformTripListSource(get(), get<CoreServices>().corrections) } binds arrayOf(TripListSource::class, TripCorrector::class)

    viewModel { CaptureViewModel(get(), System::currentTimeMillis) }
    viewModel { DrivesViewModel(get(), corrector = get<TripCorrector>()) }
    single<OnboardingSettings> { ConsentOnboardingSettings(get()) }
    viewModel {
        val consent = get<ConsentSettings>()
        val privacy = get<PrivacyChoices>()
        OnboardingViewModel(
            initialPermissions = get<PermissionReader>().read(),
            settings = get(),
            oemGuide = currentOemGuide(),
            initialChoices = ConsentChoices(consent.crashReportsAllowed, consent.usageStatisticsAllowed),
            // "Continue" on S-ONB-13: the one moment the two choices are applied (nothing is sent before it).
            onConsentCommitted = { choices ->
                privacy.setCrashReports(choices.crashReports)
                privacy.setUsageStatistics(choices.usageStatistics)
            },
        )
    }
}
