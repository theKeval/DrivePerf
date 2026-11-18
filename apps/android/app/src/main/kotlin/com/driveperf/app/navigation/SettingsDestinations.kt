package com.driveperf.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.driveperf.app.R
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.feature.settings.SettingsViewModel
import com.driveperf.feature.settings.SettingsHubScreen
import com.driveperf.feature.settings.UnitsAndDisplayScreen
import com.driveperf.feature.settings.appearanceName
import com.driveperf.feature.settings.unitName
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.text.intl.Locale

internal fun NavGraphBuilder.settingsDestinations(env: AppEnv) {
    composable(Routes.SETTINGS) {
        val settings: SettingsViewModel = koinViewModel()
        val preferences by settings.state.collectAsStateWithLifecycle()
        var health by rememberSaveable { mutableStateOf(false) }
        val summary = unitName(preferences.effectiveUnits(Locale.current.region)) +
            " · " + appearanceName(preferences.appearance)
        DetailScaffold(
            stringResource(com.driveperf.feature.settings.R.string.settings_title),
            { env.navController.popBackStack() },
        ) { padding ->
            SettingsHubScreen(
                summary, onUnits = { env.navController.navigate(Routes.UNITS_DISPLAY) },
                onRecording = { env.navController.navigate(Routes.RECORDING_SETTINGS) }, onHealth = { health = true },
                modifier = Modifier.padding(padding),
            )
            if (health) {
                val services = env.services
                SettingsHealthSheet(
                    RecordingSettingsDeps(koinInject(), services.consent, services.captureController, services.permissionReader),
                    onDismiss = { health = false }, onRecording = { env.navController.navigate(Routes.RECORDING_SETTINGS) },
                )
            }
        }
    }
    composable(Routes.UNITS_DISPLAY) {
        val settings: SettingsViewModel = koinViewModel()
        val state by settings.state.collectAsStateWithLifecycle()
        DetailScaffold(
            stringResource(com.driveperf.feature.settings.R.string.units_display),
            { env.navController.popBackStack() },
        ) { padding ->
            UnitsAndDisplayScreen(
                state, UnitSystem.defaultFor(Locale.current.region),
                settings::change, Modifier.padding(padding),
            )
            SettingsSaveError(settings)
        }
    }
}

internal fun NavGraphBuilder.recordingSettingsDestination(env: AppEnv) {
    composable(Routes.RECORDING_SETTINGS) {
        val services = env.services
        val deps = RecordingSettingsDeps(koinInject(), services.consent, services.captureController, services.permissionReader)
        val title = stringResource(R.string.recording_settings_title)
        DetailScaffold(title = title, onBack = { env.navController.popBackStack() }) { padding ->
            RecordingSettingsRoute(deps, Modifier.padding(padding), onGarage = { env.navController.navigateToTab(Tab.Garage) })
        }
    }
}

