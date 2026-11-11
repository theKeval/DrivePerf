package com.driveperf.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * S-ONB-12: the phone-brand steps (F-ONB-07). Shown only when automatic trips are on and the brand is one of Samsung,
 * Xiaomi (Redmi, POCO), OnePlus, Oppo, Vivo, Huawei or Honor; a Pixel or any other brand skips it
 * ([OnboardingFlow.skips]). The buttons open the brand's settings screen where one exists, else a system settings page:
 * the app never asks for the battery exemption itself ([BatteryExemptionPolicy], OQ-12).
 */
@Composable
fun BatteryPage(state: OnboardingUiState, actions: OnboardingActions) {
    val guide = state.oemGuide ?: return
    if (state.batteryReminded) {
        BatteryReminderPage(state, actions)
        return
    }
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            PrimaryButton(stringResource(R.string.onb_battery_done), actions.onBatteryDone)
            SecondaryButton(stringResource(R.string.onb_battery_remind), actions.onBatteryRemindLater)
        },
    ) {
        ArtTile("▭", size = 96)
        Text(
            text = stringResource(R.string.onb_battery_detected, guide.brand.label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Heading(stringResource(guide.title), stringResource(guide.lead))
        GroupCard {
            guide.steps.forEachIndexed { index, step ->
                NumberedStep(
                    number = index + 1,
                    title = stringResource(step.title),
                    path = stringResource(step.path),
                    button = step.button?.let { label ->
                        { TonalButton(stringResource(label), { actions.onOpenGuideStep(step) }) }
                    },
                )
            }
        }
    }
}

/** S-ONB-12 remindLater: the honest consequence, and the Health Check item that will keep it on the list. */
@Composable
private fun BatteryReminderPage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            PrimaryButton(stringResource(R.string.onb_continue), actions.onContinue)
            SecondaryButton(stringResource(R.string.onb_battery_show_steps), actions.onBatteryShowSteps)
        },
    ) {
        ArtTile("▭", size = 96, muted = true)
        Heading(stringResource(R.string.onb_battery_remind_title), stringResource(R.string.onb_battery_remind_lead))
        StatusRow(stringResource(R.string.onb_battery_remind_status))
    }
}
