package com.driveperf.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * S-ONB-05: the honest capture promise (Gate 1 #10, F-ONB-03). On Android "Force-quitting" reads "Force-stopping the
 * app", and the phone-brand line follows it (F-ONB-03.2). The wording may be refined; the meaning may not.
 */
@Composable
fun CapturePromisePage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        dock = { PrimaryButton(stringResource(R.string.onb_continue), actions.onContinue) },
    ) {
        Heading(stringResource(R.string.onb_promise_title), stringResource(R.string.onb_promise_lead))
        GroupCard {
            ChecklistRow(Mark.Yes, stringResource(R.string.onb_promise_detects))
            ChecklistRow(Mark.Info, stringResource(R.string.onb_promise_estimated))
            ChecklistRow(
                Mark.Info, stringResource(R.string.onb_promise_force_stop), detail = stringResource(R.string.onb_promise_force_stop_detail),
            )
            ChecklistRow(Mark.Info, stringResource(R.string.onb_promise_oem), detail = stringResource(R.string.onb_promise_oem_detail))
            ChecklistRow(Mark.Info, stringResource(R.string.onb_promise_battery))
        }
        TextButton(onClick = actions.onShowHowItWorks) { Text(stringResource(R.string.onb_promise_how_it_works)) }
    }
}
