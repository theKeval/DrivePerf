package com.driveperf.app

import com.driveperf.domain.settings.UnitSystem
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.feature.settings.SettingsViewModel
import java.util.Locale

import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.bridge.PlatformTripListSource
import com.driveperf.app.core.AndroidPlatformBindings
import com.driveperf.app.core.ConsentOnboardingSettings
import com.driveperf.app.core.DeviceId
import com.driveperf.app.core.NetworkStatus
import com.driveperf.app.replay.StandInReplayRepository
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplayProLock
import com.driveperf.feature.replay.ReplayRequest
import com.driveperf.feature.replay.ReplaySource
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
import com.driveperf.platform.wake.AlarmResumeNotice
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
    // DRIVE-100 (S2): the trip list also answers and corrects what a trip was.
    single { PlatformTripListSource(get(), get<CoreServices>().corrections) } binds arrayOf(TripListSource::class, TripCorrector::class)

    viewModel { CaptureViewModel(get(), System::currentTimeMillis, units = get()) }
    viewModel { DrivesViewModel(get(), corrector = get<TripCorrector>(), units = get()) }

    // DRIVE-200 / DRIVE-61: Replay. The repository is the R1 stand-in until CoreServices.replay merges.
    single { NetworkStatus(androidContext(), get()) }
    single<ReplayRepository> { StandInReplayRepository() }
    single { ReplayMapConfig(BuildConfig.HAS_MAPS_KEY, BuildConfig.MAPS_MAP_ID.ifBlank { null }, get<NetworkStatus>().online) }
    viewModel { (tripId: String) ->
        // ReplayProLock.NotBuilt: no entitlements yet (ADR-006); bind the 30-day Pro check here when they exist.
        ReplayViewModel(ReplayRequest(tripId, ReplaySource.TRIP_DETAIL), get(), get(), get(), ReplayProLock.NotBuilt, units = get())
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
