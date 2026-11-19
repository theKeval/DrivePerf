package com.driveperf.app.di

import com.driveperf.app.BuildConfig
import com.driveperf.app.core.NetworkStatus
import com.driveperf.di.CoreServices
import com.driveperf.di.replayPresenter
import com.driveperf.feature.replay.ReplayMapConfig
import com.driveperf.feature.replay.ReplayViewModel
import com.driveperf.presentation.replay.ReplaySource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** DRIVE-200 / DRIVE-61: Replay, from the shared model. */
val replayModule = module {
    single { NetworkStatus(androidContext(), get()) }
    single { ReplayMapConfig(BuildConfig.HAS_MAPS_KEY, BuildConfig.MAPS_MAP_ID.ifBlank { null }, get<NetworkStatus>().online) }
    viewModel { (tripId: String) ->
        // The shared presenter owns playback, the recording gate and analytics (ADR-002 section 4); the Pro lock is its
        // ReplayUnavailable.LOCKED, unset until entitlements exist (ADR-006).
        val core = get<CoreServices>()
        ReplayViewModel({ scope -> core.replayPresenter(tripId, ReplaySource.TRIP_DETAIL, scope) }, get(), units = get())
    }
}
