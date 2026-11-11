package com.driveperf.feature.onboarding

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

/** S-ONB-04: the 18+ check and the terms. Continue stays disabled until the box is ticked, and it is never pre-ticked. */
@Composable
fun AgeTermsPage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        style = PageStyle(centered = true),
        dock = {
            PrimaryButton(stringResource(R.string.onb_continue), actions.onAgeContinue, enabled = state.ageConfirmed)
            SecondaryButton(stringResource(R.string.onb_age_under18), actions.onUnder18)
        },
    ) {
        ArtTile("18+")
        Heading(stringResource(R.string.onb_age_title), stringResource(R.string.onb_age_lead), centered = true)
        GroupCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = state.ageConfirmed, role = Role.Checkbox, onValueChange = actions.onAgeConfirmedChanged),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.ageConfirmed, onCheckedChange = null)
                Text(stringResource(R.string.onb_age_checkbox), style = MaterialTheme.typography.titleMedium)
            }
            TermsLine(actions)
        }
    }
}

/** "By continuing you agree to the Terms of Use and Privacy Policy." Both links open in a Custom Tab (F-ONB-02.3). */
@Composable
private fun TermsLine(actions: OnboardingActions) {
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val text = buildAnnotatedString {
        append(stringResource(R.string.onb_terms_prefix))
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { actions.onOpenTerms() }) {
            append(stringResource(R.string.onb_terms_link))
        }
        append(stringResource(R.string.onb_terms_and))
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { actions.onOpenPrivacy() }) {
            append(stringResource(R.string.onb_privacy_link))
        }
        append(stringResource(R.string.onb_terms_suffix))
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** S-ONB-04 under-18: onboarding stops here. Only a local flag is stored (F-ONB-02.2). */
@Composable
fun Under18Page(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        style = PageStyle(centered = true),
        dock = { SecondaryButton(stringResource(R.string.onb_age_blocked_undo), actions.onUnder18Undone) },
    ) {
        ArtTile("◎", muted = true, size = 96)
        Heading(stringResource(R.string.onb_age_blocked_title), stringResource(R.string.onb_age_blocked_lead), centered = true)
    }
}
