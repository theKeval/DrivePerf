package com.driveperf.feature.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * S-ONB-08: automatic trips are **off until the user turns them on** (Law 25 default, F-ONB-04.2). This is also the Play
 * prominent disclosure (F-ONB-04.2): in the app, before any background-location request, using the word "location", saying
 * "even when the app is closed or not in use" and naming the feature, with an affirmative action (toggle on, Continue).
 */
@Composable
fun AutoTripsPage(state: OnboardingUiState, actions: OnboardingActions) {
    val needsSettingsStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            val label = when {
                !state.autoTripsOn -> R.string.onb_auto_cta_manual
                needsSettingsStep -> R.string.onb_auto_cta_settings
                else -> R.string.onb_continue
            }
            PrimaryButton(stringResource(label), actions.onAutoTripsContinue)
        },
    ) {
        Heading(stringResource(R.string.onb_auto_title), stringResource(R.string.onb_auto_lead))
        GroupCard(highlighted = true) {
            Text(
                stringResource(R.string.onb_auto_kicker),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.onb_auto_disclosure), style = MaterialTheme.typography.bodyLarge)
            DefinitionRow(stringResource(R.string.onb_auto_what_term), stringResource(R.string.onb_auto_what))
            DefinitionRow(stringResource(R.string.onb_auto_why_term), stringResource(R.string.onb_auto_why))
            DefinitionRow(stringResource(R.string.onb_auto_where_term), stringResource(R.string.onb_auto_where))
        }
        AutoTripsToggle(state, actions, needsSettingsStep)
        if (state.autoTripsDeclined) {
            GroupCard { Text(stringResource(R.string.onb_auto_declined), style = MaterialTheme.typography.bodyMedium) }
        }
        Text(
            stringResource(R.string.onb_auto_fine),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutoTripsToggle(state: OnboardingUiState, actions: OnboardingActions, needsSettingsStep: Boolean) {
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = state.autoTripsOn, role = Role.Switch, onValueChange = actions.onAutoTripsToggled),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.onb_auto_toggle), style = MaterialTheme.typography.titleMedium)
                val sub = when {
                    !state.autoTripsOn -> R.string.onb_auto_toggle_off
                    needsSettingsStep -> R.string.onb_auto_toggle_on
                    else -> R.string.onb_auto_toggle_on_dialog
                }
                Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.autoTripsOn, onCheckedChange = null)
        }
    }
}

/**
 * S-ONB-09: Android 11 and later grant background location only in Settings. The page says exactly what to tap; when the
 * user comes back the route reads the permission on resume (well inside a second, F-ONB-04.4) and the page shows the result.
 */
@Composable
fun AllTheTimePage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            when (state.allTheTime) {
                AllTheTimeResult.Granted -> PrimaryButton(stringResource(R.string.onb_continue), actions.onContinue)
                AllTheTimeResult.NotGranted -> {
                    PrimaryButton(stringResource(R.string.onb_allday_try_again), actions.onOpenLocationSettings)
                    SecondaryButton(stringResource(R.string.onb_allday_manual), actions.onContinueInManualMode)
                }
                AllTheTimeResult.Waiting -> {
                    PrimaryButton(stringResource(R.string.onb_allday_open), actions.onOpenLocationSettings)
                    SecondaryButton(stringResource(R.string.onb_allday_manual), actions.onContinueInManualMode)
                }
            }
        },
    ) {
        when (state.allTheTime) {
            AllTheTimeResult.Granted -> {
                ArtTile("✓")
                Heading(stringResource(R.string.onb_allday_granted_title), stringResource(R.string.onb_allday_granted_lead))
            }
            AllTheTimeResult.NotGranted -> {
                Heading(stringResource(R.string.onb_allday_not_title), stringResource(R.string.onb_allday_not_lead))
                Steps()
            }
            AllTheTimeResult.Waiting -> {
                Heading(stringResource(R.string.onb_allday_title), stringResource(R.string.onb_allday_lead))
                Steps()
            }
        }
    }
}

@Composable
private fun Steps() {
    GroupCard {
        NumberedStep(1, stringResource(R.string.onb_allday_step_1))
        NumberedStep(2, stringResource(R.string.onb_allday_step_2))
        NumberedStep(3, stringResource(R.string.onb_allday_step_3))
    }
}
