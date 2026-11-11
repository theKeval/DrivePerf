package com.driveperf.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DpSwitchRow

/** The onboarding pages (WP-A, S-ONB-01 to S-ONB-16, Android order: location, then all-the-time, then activity, then notifications). */
@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            when (state.step) {
                OnboardingStep.Welcome, OnboardingStep.HowItWorks, OnboardingStep.Battery -> InfoStep(state.step, actions)
                OnboardingStep.Location, OnboardingStep.BackgroundLocation,
                OnboardingStep.ActivityRecognition, OnboardingStep.Notifications,
                -> PermissionStep(state, actions)
                OnboardingStep.HelpImprove -> HelpImproveStep(state, actions)
                OnboardingStep.Ready -> ReadyStep(state, actions)
            }
        }
    }
}

@Composable
private fun InfoStep(step: OnboardingStep, actions: OnboardingActions) {
    when (step) {
        OnboardingStep.Welcome -> Page(
            title = R.string.onb_welcome_title,
            body = R.string.onb_welcome_body,
            primary = R.string.onb_welcome_cta to actions.onContinue,
        )
        OnboardingStep.HowItWorks -> Page(
            title = R.string.onb_how_title,
            body = R.string.onb_how_body,
            primary = R.string.onb_continue to actions.onContinue,
            secondary = R.string.onb_back to actions.onBack,
        ) {
            Bullet(R.string.onb_how_honest)
            Bullet(R.string.onb_how_passenger)
        }
        else -> Page(
            title = R.string.onb_battery_title,
            body = R.string.onb_battery_body,
            primary = R.string.onb_battery_open to {
                actions.onOpenBatterySettings()
                actions.onContinue()
            },
            secondary = R.string.onb_battery_later to actions.onContinue,
        )
    }
}

/** What differs between the four permission pages. */
private class PermissionPageSpec(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val denied: Int,
    val onAllow: () -> Unit,
    @StringRes val extra: Int? = null,
)

@Composable
private fun PermissionStep(state: OnboardingUiState, actions: OnboardingActions) {
    val spec = when (state.step) {
        OnboardingStep.Location -> PermissionPageSpec(
            title = R.string.onb_location_title,
            body = R.string.onb_location_body,
            denied = if (state.permissions.approximateOnly) R.string.onb_location_approximate else R.string.onb_location_denied,
            onAllow = actions.onRequestLocation,
        )
        OnboardingStep.BackgroundLocation -> PermissionPageSpec(
            title = R.string.onb_background_title,
            body = R.string.onb_background_body,
            denied = R.string.onb_background_denied,
            onAllow = actions.onRequestBackground,
            extra = R.string.onb_background_disclosure,
        )
        OnboardingStep.ActivityRecognition -> PermissionPageSpec(
            title = R.string.onb_activity_title,
            body = R.string.onb_activity_body,
            denied = R.string.onb_activity_denied,
            onAllow = actions.onRequestActivity,
        )
        else -> PermissionPageSpec(
            title = R.string.onb_notifications_title,
            body = R.string.onb_notifications_body,
            denied = R.string.onb_notifications_denied,
            onAllow = actions.onRequestNotifications,
        )
    }
    val wasDenied = state.deniedStep == state.step
    Page(
        title = spec.title,
        body = spec.body,
        primary = (if (wasDenied) R.string.onb_open_settings else R.string.onb_allow) to
            (if (wasDenied) actions.onOpenAppSettings else spec.onAllow),
        secondary = (if (wasDenied) R.string.onb_continue else R.string.onb_not_now) to actions.onContinue,
    ) {
        if (spec.extra != null) Bullet(spec.extra)
        if (wasDenied) {
            Text(
                text = stringResource(spec.denied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HelpImproveStep(state: OnboardingUiState, actions: OnboardingActions) {
    Page(
        title = R.string.onb_help_title,
        body = R.string.onb_help_body,
        primary = R.string.onb_continue to actions.onContinue,
        secondary = R.string.onb_back to actions.onBack,
    ) {
        DpSwitchRow(
            title = stringResource(R.string.onb_help_stats_title),
            description = stringResource(R.string.onb_help_stats_body),
            checked = state.usageStatistics,
            onCheckedChange = actions.onUsageStatisticsChanged,
        )
        DpSwitchRow(
            title = stringResource(R.string.onb_help_crash_title),
            description = stringResource(R.string.onb_help_crash_body),
            checked = state.crashReports,
            onCheckedChange = actions.onCrashReportsChanged,
        )
    }
}

@Composable
private fun ReadyStep(state: OnboardingUiState, actions: OnboardingActions) {
    Page(
        title = R.string.onb_ready_title,
        body = R.string.onb_ready_body,
        primary = R.string.onb_ready_cta to actions.onContinue,
    ) {
        Bullet(if (state.permissions.fineLocation) R.string.onb_ready_location_on else R.string.onb_ready_location_off)
        Bullet(if (state.permissions.backgroundLocation) R.string.onb_ready_auto_on else R.string.onb_ready_auto_off)
    }
}

@Composable
private fun Page(
    @StringRes title: Int,
    @StringRes body: Int,
    primary: Pair<Int, () -> Unit>,
    secondary: Pair<Int, () -> Unit>? = null,
    content: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(title), style = MaterialTheme.typography.headlineLargeEmphasized)
            Text(text = stringResource(body), style = MaterialTheme.typography.bodyLarge)
            content()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            Button(onClick = primary.second, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(primary.first))
            }
            if (secondary != null) {
                TextButton(onClick = secondary.second, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(secondary.first))
                }
            }
        }
    }
}

@Composable
private fun Bullet(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
