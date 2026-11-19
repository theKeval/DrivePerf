package com.driveperf.app.navigation

import com.driveperf.presentation.drives.DrivesEffect

/**
 * The Drives presenter's navigation: Replay opens S-RPL-01, Analysis opens S-ANL-01. While a drive records, the in-drive
 * gate covers the whole graph, so neither can open then (F-RPL-01 AC6).
 */
internal fun AppEnv.onDrivesEffect(effect: DrivesEffect) {
    when (effect) {
        is DrivesEffect.OpenReplay -> navController.navigate(Routes.replay(effect.tripId, effect.title))
        is DrivesEffect.OpenAnalysis -> navController.navigate(Routes.analysis(effect.tripId, effect.title))
    }
}
