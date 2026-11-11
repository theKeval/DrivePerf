@file:OptIn(ExperimentalMaterial3Api::class)

package com.driveperf.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The sheets over a page: S-ONB-05 How it works, S-ONB-11 precise location, S-ONB-16 denied and manual mode. */
@Composable
fun OnboardingSheets(state: OnboardingUiState, actions: OnboardingActions) {
    val sheet = state.sheet ?: return
    ModalBottomSheet(onDismissRequest = actions.onDismissSheet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (sheet) {
                OnboardingSheet.HowItWorks -> HowItWorksSheet(actions)
                OnboardingSheet.PreciseLocation -> PreciseLocationSheet(state, actions)
                OnboardingSheet.LocationDenied -> LocationDeniedSheet(actions)
                OnboardingSheet.ManualMode -> ManualModeSheet(actions)
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, glyph: String, positive: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphDisc(glyph, positive = positive, size = 36)
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
    }
}

/** S-ONB-05 sheet: manual vs automatic trips, with a link to S-WEB-06 in the in-app browser (F-ONB-03.3). */
@Composable
private fun HowItWorksSheet(actions: OnboardingActions) {
    SheetHeader(stringResource(R.string.onb_how_sheet_title), "i")
    Text(
        stringResource(R.string.onb_how_sheet_lead),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    GroupCard {
        ChecklistRow(Mark.Yes, stringResource(R.string.onb_how_auto_title), detail = stringResource(R.string.onb_how_auto_body))
        ChecklistRow(Mark.Yes, stringResource(R.string.onb_how_manual_title), detail = stringResource(R.string.onb_how_manual_body))
    }
    TextButton(onClick = actions.onOpenHowItRecords) { Text(stringResource(R.string.onb_how_more)) }
    PrimaryButton(stringResource(R.string.onb_done), actions.onDismissSheet)
}

/** S-ONB-11: why precise location matters, and the refused state ("route only"). */
@Composable
private fun PreciseLocationSheet(state: OnboardingUiState, actions: OnboardingActions) {
    if (state.preciseRefused) {
        SheetHeader(stringResource(R.string.onb_precise_refused_title), "≈")
        GroupCard { Text(stringResource(R.string.onb_precise_refused_card), style = MaterialTheme.typography.bodyLarge) }
        Text(
            stringResource(R.string.onb_precise_refused_where),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(stringResource(R.string.onb_continue), actions.onPreciseContinue)
    } else {
        SheetHeader(stringResource(R.string.onb_precise_title), "◎")
        Text(stringResource(R.string.onb_precise_lead), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ComparisonTile(stringResource(R.string.onb_precise_approximate), "○", Modifier.weight(1f))
            ComparisonTile(stringResource(R.string.onb_precise_precise), "●", Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.onb_precise_fine),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(stringResource(R.string.onb_precise_use), actions.onRequestPrecise)
        SecondaryButton(stringResource(R.string.onb_precise_keep), actions.onKeepApproximate)
    }
}

@Composable
private fun ComparisonTile(caption: String, mark: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ArtTile(mark, size = 72, muted = mark == "○")
        Text(caption, style = MaterialTheme.typography.labelLarge)
    }
}

/** S-ONB-16 default: location was refused, so nothing records (F-ONB-04.6). */
@Composable
private fun LocationDeniedSheet(actions: OnboardingActions) {
    SheetHeader(stringResource(R.string.onb_denied_title), "!", positive = false)
    Text(stringResource(R.string.onb_denied_lead), style = MaterialTheme.typography.bodyLarge)
    GroupCard {
        ChecklistRow(Mark.Yes, stringResource(R.string.onb_denied_check_1))
        ChecklistRow(Mark.Yes, stringResource(R.string.onb_denied_check_2))
        ChecklistRow(Mark.No, stringResource(R.string.onb_denied_check_3))
    }
    PrimaryButton(stringResource(R.string.onb_open_settings), actions.onOpenAppSettings)
    SecondaryButton(stringResource(R.string.onb_not_now), actions.onContinueWithoutLocation)
    Text(
        stringResource(R.string.onb_denied_fine),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** S-ONB-16 alwaysDeclined: automatic trips could not be turned on, so the app stays in manual mode. */
@Composable
private fun ManualModeSheet(actions: OnboardingActions) {
    SheetHeader(stringResource(R.string.onb_manual_title), "●")
    Text(stringResource(R.string.onb_manual_lead), style = MaterialTheme.typography.bodyLarge)
    GroupCard {
        ChecklistRow(Mark.Yes, stringResource(R.string.onb_manual_check_1), detail = stringResource(R.string.onb_manual_check_1_detail))
        ChecklistRow(Mark.Yes, stringResource(R.string.onb_manual_check_2))
        ChecklistRow(Mark.No, stringResource(R.string.onb_manual_check_3), detail = stringResource(R.string.onb_manual_check_3_detail))
    }
    PrimaryButton(stringResource(R.string.onb_manual_cta), actions.onContinueInManualMode)
    SecondaryButton(stringResource(R.string.onb_open_settings), actions.onOpenAppSettings)
}
