package com.driveperf.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** S-ONB-06: the primer is always shown before the system dialog (F-ONB-04.1). "Not now" never triggers the dialog. */
@Composable
fun LocationPage(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            PrimaryButton(stringResource(R.string.onb_continue), actions.onRequestLocation)
            SecondaryButton(stringResource(R.string.onb_not_now), actions.onLocationNotNow)
        },
    ) {
        if (state.deniedStep == OnboardingStep.Location) {
            ArtTile("⌖", muted = true, size = 96)
            Heading(stringResource(R.string.onb_location_denied_title), stringResource(R.string.onb_location_denied_lead))
            StatusRow(stringResource(R.string.onb_location_denied_status))
            PrimaryButton(stringResource(R.string.onb_open_settings), actions.onOpenAppSettings)
            SecondaryButton(stringResource(R.string.onb_location_continue_without), actions.onContinueWithoutLocation)
        } else {
            ArtTile("⌖", size = 96)
            Heading(stringResource(R.string.onb_location_title), stringResource(R.string.onb_location_lead))
            GroupCard {
                ChecklistRow(Mark.Yes, stringResource(R.string.onb_location_check_1))
                ChecklistRow(Mark.Yes, stringResource(R.string.onb_location_check_2))
                ChecklistRow(Mark.No, stringResource(R.string.onb_location_check_3))
            }
            Text(
                stringResource(R.string.onb_location_fine),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** S-ONB-07: "Physical activity" on Android (API 29+). Refused: capture still runs on location alone (F-ONB-05.2). */
@Composable
fun MotionPage(state: OnboardingUiState, actions: OnboardingActions) {
    val denied = state.deniedStep == OnboardingStep.Motion
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            if (denied) {
                PrimaryButton(stringResource(R.string.onb_continue), actions.onContinue)
                SecondaryButton(stringResource(R.string.onb_open_settings), actions.onOpenAppSettings)
            } else {
                PrimaryButton(stringResource(R.string.onb_continue), actions.onRequestActivity)
            }
        },
    ) {
        ArtTile("◔", muted = denied, size = 96)
        if (denied) {
            Heading(stringResource(R.string.onb_motion_denied_title), stringResource(R.string.onb_motion_denied_lead))
        } else {
            Heading(stringResource(R.string.onb_motion_title), stringResource(R.string.onb_motion_lead))
            GroupCard {
                ChecklistRow(
                    Mark.Yes, stringResource(R.string.onb_motion_check_1), detail = stringResource(R.string.onb_motion_check_1_detail),
                )
                ChecklistRow(Mark.Yes, stringResource(R.string.onb_motion_check_2))
            }
        }
    }
}

/**
 * S-ONB-10: exactly what we send, and "never while you drive" (F-ONB-06.1). Android 12 and older have no runtime
 * permission, so this page is skipped there. Refused: the recording notification still shows, it is required.
 */
@Composable
fun NotificationsPage(state: OnboardingUiState, actions: OnboardingActions) {
    val denied = state.deniedStep == OnboardingStep.Notifications
    OnboardingPage(
        state = state,
        actions = actions,
        dock = {
            if (denied) {
                PrimaryButton(stringResource(R.string.onb_continue), actions.onContinue)
                SecondaryButton(stringResource(R.string.onb_open_settings), actions.onOpenAppSettings)
            } else {
                PrimaryButton(stringResource(R.string.onb_continue), actions.onRequestNotifications)
                SecondaryButton(stringResource(R.string.onb_not_now), actions.onContinue)
            }
        },
    ) {
        if (denied) {
            Heading(stringResource(R.string.onb_notifications_denied_title), stringResource(R.string.onb_notifications_denied_lead))
            GroupCard {
                Text(stringResource(R.string.onb_notifications_fgs), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Heading(stringResource(R.string.onb_notifications_title), stringResource(R.string.onb_notifications_lead))
            NotificationPreview(R.string.onb_notifications_preview_1_title, R.string.onb_notifications_preview_1_body)
            NotificationPreview(R.string.onb_notifications_preview_2_title, R.string.onb_notifications_preview_2_body)
            NotificationPreview(R.string.onb_notifications_preview_3_title, R.string.onb_notifications_preview_3_body)
            GroupCard {
                ChecklistRow(Mark.Yes, stringResource(R.string.onb_notifications_list_1))
                ChecklistRow(Mark.Yes, stringResource(R.string.onb_notifications_list_2))
                ChecklistRow(Mark.Yes, stringResource(R.string.onb_notifications_list_3))
                ChecklistRow(Mark.No, stringResource(R.string.onb_notifications_list_4))
            }
        }
    }
}

@Composable
private fun NotificationPreview(@StringRes title: Int, @StringRes body: Int) {
    GroupCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphDisc("◎", size = 36)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.onb_notifications_now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Location: Don't allow": the status in words, so colour is never the only signal. */
@Composable
fun StatusRow(text: String) {
    GroupCard { Text(text, style = MaterialTheme.typography.titleMedium) }
}
