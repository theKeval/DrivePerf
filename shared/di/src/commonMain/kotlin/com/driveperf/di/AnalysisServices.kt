package com.driveperf.di

import com.driveperf.data.local.LocalAnalysisPreferences
import com.driveperf.domain.analysis.AnalysisPreferences
import com.driveperf.domain.analysis.AnalysisRepository
import com.driveperf.domain.analysis.TripAnalysisRepository
import com.driveperf.presentation.analysis.AnalysisPresenter
import com.driveperf.presentation.analysis.AnalysisSource
import kotlinx.coroutines.CoroutineScope

/**
 * The shared Analysis presenter for [tripId] (S-ANL-01, F-RPL-05, F-RPL-06), running in a child of [scope]. Android
 * passes `viewModelScope`. [region] picks the default units until the user chooses some. Call `close()` when the
 * screen goes away.
 */
public fun CoreServices.analysisPresenter(
    tripId: String,
    source: AnalysisSource,
    region: String,
    scope: CoroutineScope,
): AnalysisPresenter = AnalysisPresenter(tripId, source, analyses(), preferences(), units(region), analytics, scope)

/** [analysisPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.analysisPresenter(tripId: String, source: AnalysisSource, region: String): AnalysisPresenter =
    AnalysisPresenter.onMain(tripId, source, analyses(), preferences(), units(region), analytics)

/** The wave-0 factory: from the trip detail, with the units the user chose (metric until they choose). */
public fun CoreServices.analysisPresenter(tripId: String, scope: CoroutineScope): AnalysisPresenter =
    analysisPresenter(tripId, AnalysisSource.TRIP_DETAIL, "", scope)

/** The wave-0 factory for Swift. */
public fun CoreServices.analysisPresenter(tripId: String): AnalysisPresenter =
    analysisPresenter(tripId, AnalysisSource.TRIP_DETAIL, "")

/** Analysis reads the trip, its R1 file, its preview track and its Replay (DRIVE-45). */
private fun CoreServices.analyses(): AnalysisRepository = TripAnalysisRepository(trips, replay, analysisFiles, previews)

/** The channel and axis choice in the local profile (F-RPL-05 AC3–4). */
private fun CoreServices.preferences(): AnalysisPreferences = LocalAnalysisPreferences(db, io, clock)
