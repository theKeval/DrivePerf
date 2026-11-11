package com.driveperf.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** S-ONB-01. No permission prompt can appear here or on the next two pages (F-ONB-01.1). */
@Composable
fun WelcomePage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        style = PageStyle(showSkip = true, centered = true),
        dock = {
            PageDots(current = 0)
            PrimaryButton(stringResource(R.string.onb_welcome_cta), actions.onContinue)
        },
    ) {
        ArtTile("◎", size = 160)
        Text(
            text = stringResource(R.string.onb_welcome_wordmark),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onb_welcome_support),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** S-ONB-02: an example Story, so the first thing the user sees is what they get. */
@Composable
fun StoryPreviewPage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        style = PageStyle(showSkip = true, centered = true),
        dock = {
            PageDots(current = 1)
            PrimaryButton(stringResource(R.string.onb_next), actions.onContinue)
        },
    ) {
        val description = stringResource(R.string.onb_story_card_description)
        GroupCard(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            Text(
                stringResource(R.string.onb_story_kicker).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.onb_story_card_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ArtTile("92", size = 96)
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.onb_story_delta)) },
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        Heading(stringResource(R.string.onb_story_title), stringResource(R.string.onb_story_lead), centered = true)
    }
}

/** S-ONB-03: the privacy line and the Pro Preview, with no card. */
@Composable
fun PrivacyPage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            PageDots(current = 2)
            PrimaryButton(stringResource(R.string.onb_continue), actions.onContinue)
        },
    ) {
        Heading(stringResource(R.string.onb_privacy_title), stringResource(R.string.onb_privacy_lead))
        GroupCard {
            ChecklistRow(Mark.Yes, stringResource(R.string.onb_privacy_check_1))
            ChecklistRow(Mark.No, stringResource(R.string.onb_privacy_check_2))
            ChecklistRow(Mark.Yes, stringResource(R.string.onb_privacy_check_3))
        }
        GroupCard(highlighted = true) {
            Text(
                stringResource(R.string.onb_preview_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.onb_preview_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.onb_preview_line),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Three page dots, the current one a wider capsule (S-ONB-01 to S-ONB-03). */
@Composable
fun PageDots(current: Int, total: Int = 3) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { index ->
            Surface(
                shape = CircleShape,
                color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(width = if (index == current) 18.dp else 6.dp, height = 6.dp),
            ) {}
        }
    }
}

