package com.driveperf.app

import com.driveperf.domain.settings.UnitSystem
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.feature.settings.SettingsViewModel
import java.util.Locale

import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.core.AndroidPlatformBindings
import com.driveperf.app.core.ConsentOnboardingSettings
import com.driveperf.app.core.DeviceId
import com.driveperf.app.core.NetworkStatus
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.presentation.replay.ReplaySource
import com.driveperf.feature.replay.ReplayViewModel
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
import com.driveperf.feature.drives.JavaTimeDrivesFormatter
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
import com.driveperf.platform.wake.AlarmResumeNotice
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
    single { ConsentStore(androidContext()) } bind ConsentSettings::class
    single { PermissionReader(androidContext()) }
    single<CrashReporter> { SentryCrashReporter(androidContext(), BuildConfig.SENTRY_DSN, BuildConfig.VERSION_NAME) }

    // DRIVE-70: analytics port. No Firebase project yet (DRIVE-42), so the sink is a stub until it exists.
    single<AnalyticsSink> { StubAnalyticsSink() }

    single<PlatformBindings> {
        AndroidPlatformBindings(
            get(), get<FixWatch>(), get(), get(), get(), get<ParkedRegionMonitor>(), get(), DeviceId.of(androidContext()), get(),
            AlarmResumeNotice(androidContext()),
        )
    }
    single<CoreServices> { DrivePerfCore.start(get(), androidContext()) }
    single<SettingsRepository> { get<CoreServices>().settings }
    single<kotlinx.coroutines.flow.StateFlow<UnitSystem>> {
        val repository = get<SettingsRepository>()
        val region = Locale.getDefault().country
        repository.state.map { it.effectiveUnits(region) }.stateIn(
            get<CoroutineScope>(), kotlinx.coroutines.flow.SharingStarted.Eagerly, repository.state.value.effectiveUnits(region),
        )
    }
    viewModel { SettingsViewModel(get()) }
    single<TripRecorder> { get<CoreServices>().recorder }
    single<Analytics> { get<CoreServices>().analytics }
    single { PrivacyChoices(get(), get(), get()) }
    single<TripRepository> { get<CoreServices>().trips }
    single { WakePath(androidContext(), get(), get(), get()) { get<ConsentSettings>().automaticTripsEnabled } }

    single {
        PlatformCaptureController(androidContext(), get(), get(), get(), get(), get(), get(), get(), get<WakePath>()::refresh)
    } bind CaptureController::class

    viewModel { CaptureViewModel(get(), System::currentTimeMillis, units = get()) }
    // DRIVE-217: the shared presenter owns the Drives lists, sheets, answers and corrections (ADR-002 section 4).
    viewModel {
        val core = get<CoreServices>()
        DrivesViewModel { scope -> core.drivesPresenter(JavaTimeDrivesFormatter(), Locale.getDefault().country, scope) }
    }

    // DRIVE-200 / DRIVE-61: Replay, from the shared model.
    single { NetworkStatus(androidContext(), get()) }
    single { ReplayMapConfig(BuildConfig.HAS_MAPS_KEY, BuildConfig.MAPS_MAP_ID.ifBlank { null }, get<NetworkStatus>().online) }
    viewModel { (tripId: String) ->
        // The shared presenter owns playback, the recording gate and analytics (ADR-002 section 4); the Pro lock is its
        // ReplayUnavailable.LOCKED, unset until entitlements exist (ADR-006).
        val core = get<CoreServices>()
        ReplayViewModel({ scope -> core.replayPresenter(tripId, ReplaySource.TRIP_DETAIL, scope) }, get(), units = get())
    }
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
