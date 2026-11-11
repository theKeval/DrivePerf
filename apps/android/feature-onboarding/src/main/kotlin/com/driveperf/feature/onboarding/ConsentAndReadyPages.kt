package com.driveperf.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column

/**
 * S-ONB-13: two separate choices, usage statistics never pre-selected (F-ONB-08.1). "Continue" has the same label and
 * weight whatever the choices (no "Accept all"). Nothing is sent before it, and events held until now are dropped if
 * usage statistics are off (F-ONB-08.2).
 */
@Composable
fun HelpImprovePage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        dock = { PrimaryButton(stringResource(R.string.onb_continue), actions.onHelpContinue) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArtTile("▦", size = 64)
            ArtTile("⛨", size = 64)
        }
        Heading(stringResource(R.string.onb_help_title), stringResource(R.string.onb_help_lead))
        ChoiceCard(
            title = stringResource(R.string.onb_help_stats_title),
            body = stringResource(R.string.onb_help_stats_body),
            checked = state.usageStatistics,
            onCheckedChange = actions.onUsageStatisticsChanged,
        )
        ChoiceCard(
            title = stringResource(R.string.onb_help_crash_title),
            body = stringResource(R.string.onb_help_crash_body),
            checked = state.crashReports,
            onCheckedChange = actions.onCrashReportsChanged,
        )
        Text(
            stringResource(R.string.onb_help_fine),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceCard(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GroupCard(highlighted = checked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = "$title. $body" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** S-ONB-15: the truth about what is on. "Auto-detect off" is also what Home shows afterwards, with a fix action. */
@Composable
fun ReadyPage(state: OnboardingUiState, actions: OnboardingActions) {
    val manual = !state.autoTripsOn
    OnboardingPage(
        state = state,
        actions = actions,
        style = PageStyle(centered = true),
        dock = { PrimaryButton(stringResource(R.string.onb_ready_cta), actions.onContinue) },
    ) {
        ArtTile("✓", size = 96)
        Heading(
            stringResource(if (manual) R.string.onb_ready_title_manual else R.string.onb_ready_title_auto),
            stringResource(if (manual) R.string.onb_ready_lead_manual else R.string.onb_ready_lead_auto),
            centered = true,
        )
        Text(
            stringResource(R.string.onb_ready_preview),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        GroupCard {
            SummaryRow(stringResource(R.string.onb_ready_automatic), on = !manual)
            SummaryRow(stringResource(R.string.onb_ready_location), on = state.permissions.hasLocation, valueOn = locationText(state))
            SummaryRow(stringResource(R.string.onb_ready_activity), on = state.permissions.activityRecognition)
            SummaryRow(stringResource(R.string.onb_ready_notifications), on = state.permissions.notifications)
        }
    }
}

@Composable
private fun locationText(state: OnboardingUiState): String? = when {
    state.permissions.fineLocation -> stringResource(R.string.onb_ready_location_precise)
    state.permissions.approximateOnly -> stringResource(R.string.onb_ready_location_approximate)
    else -> null
}

/** "Automatic trips: Off". The value is always words, never colour alone. */
@Composable
private fun SummaryRow(label: String, on: Boolean, valueOn: String? = null) {
    val value = if (on) valueOn ?: stringResource(R.string.onb_status_on) else stringResource(R.string.onb_status_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
