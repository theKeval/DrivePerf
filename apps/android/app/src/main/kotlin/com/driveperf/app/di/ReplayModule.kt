package com.driveperf.app.di

import com.driveperf.app.BuildConfig
import com.driveperf.app.core.NetworkStatus
import com.driveperf.di.CoreServices
import com.driveperf.di.evidencePresenter
import com.driveperf.di.replayPresenter
import com.driveperf.di.tipDetailPresenter
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplaySheets
import com.driveperf.feature.replay.ReplayViewModel
import com.driveperf.presentation.replay.ReplaySource
import com.driveperf.presentation.replay.ReplayTimeFormat
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** DRIVE-200 / DRIVE-61: Replay, from the shared model; DRIVE-34/39/203/97/102: its pins, evidence and tips. */
val replayModule = module {
    single { NetworkStatus(androidContext(), get()) }
    single { ReplayMapConfig(BuildConfig.HAS_MAPS_KEY, BuildConfig.MAPS_MAP_ID.ifBlank { null }, get<NetworkStatus>().online) }
    viewModel { (tripId: String) ->
        // The shared presenter owns playback, the pins, the recording gate and analytics (ADR-002 section 4); the Pro
        // lock is its ReplayUnavailable.LOCKED, unset until entitlements exist (ADR-006).
        val core = get<CoreServices>()
        val region = Locale.getDefault().country
        val format = localTimeOfDay()
        ReplayViewModel(
            presenterFactory = { scope -> core.replayPresenter(tripId, ReplaySource.TRIP_DETAIL, region, format, scope) },
            maps = get(),
            units = get(),
            sheets = ReplaySheets(
                evidence = { eventId, scope -> core.evidencePresenter(tripId, eventId, region, format, scope) },
                tip = { tipId, eventId, scope -> core.tipDetailPresenter(tripId, tipId, eventId, region, scope) },
            ),
        )
    }
}

/** "5:56 PM" in the phone's time zone and locale: the only formatting the shared Replay copy leaves to the app. */
private fun localTimeOfDay(): ReplayTimeFormat {
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    return ReplayTimeFormat { ms -> formatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())) }
}
