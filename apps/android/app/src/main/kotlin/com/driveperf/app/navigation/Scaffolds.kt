package com.driveperf.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.driveperf.app.R
import com.driveperf.designsystem.DpAvatarButton
import com.driveperf.designsystem.DpIcons
import com.driveperf.feature.capture.AttentionBanner
import com.driveperf.feature.capture.AutoDetectMode
import com.driveperf.feature.capture.AutoDetectStatus
import com.driveperf.feature.capture.CaptureStatusActions
import com.driveperf.feature.capture.CaptureStatusSheet
import com.driveperf.feature.capture.CaptureToolbar
import com.driveperf.feature.capture.CaptureToolbarActions
import com.driveperf.feature.capture.CaptureUiState
import com.driveperf.feature.capture.ForceStopNotice

/**
 * The shell of a tab root (S-NAV-01): a short M3 Expressive navigation bar with the four tabs, the profile avatar at the
 * top trailing edge, and, on the Drives tab only, the floating capture toolbar docked above the bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabScaffold(
    selected: Tab,
    capture: CaptureUiState,
    callbacks: ShellCallbacks,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val autoDetect = callbacks.autoDetect
    var showStatus by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selected.label)) },
                actions = {
                    DpAvatarButton(
                        onClick = callbacks.onProfile,
                        contentDescription = stringResource(R.string.profile_button_description),
                    )
                },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                for (tab in Tab.entries) {
                    ShortNavigationBarItem(
                        selected = tab == selected,
                        onClick = { callbacks.onTabSelected(tab) },
                        icon = { Icon(painter = painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selected == Tab.Drives) {
                CaptureToolbar(
                    state = capture,
                    autoDetect = autoDetect,
                    actions = CaptureToolbarActions(
                        onStart = callbacks.onStart,
                        onOpenDrive = callbacks.onOpenDrive,
                        onFixLocation = callbacks.onFixLocation,
                        onOpenStatus = { showStatus = true },
                    ),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        content = { padding ->
            if (selected == Tab.Drives && autoDetect != null) {
                Column(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
                    HomeBanners(autoDetect, callbacks, onOpenStatus = { showStatus = true })
                    Box(modifier = Modifier.weight(1f)) { content(PaddingValues(bottom = padding.calculateBottomPadding())) }
                }
            } else {
                content(padding)
            }
        },
    )
    if (showStatus && selected == Tab.Drives && autoDetect != null) {
        StatusSheet(autoDetect, callbacks, onDismiss = { showStatus = false })
    }
}

@Composable
private fun StatusSheet(autoDetect: AutoDetectStatus, callbacks: ShellCallbacks, onDismiss: () -> Unit) {
    CaptureStatusSheet(
        status = autoDetect,
        actions = CaptureStatusActions(
            onDismiss = onDismiss,
            onFix = callbacks.onFixReason,
            onOpenRecordingSettings = {
                onDismiss()
                callbacks.onOpenRecordingSettings()
            },
        ),
    )
}

/**
 * The Drives tab's banner slot (S-HOME-01): the force-stop notice, then the needs-attention banner naming the first reason
 * (its Fix opens the capture status sheet, as on iOS) or "Auto-detect off" when the user chose manual trips.
 */
@Composable
private fun HomeBanners(autoDetect: AutoDetectStatus, callbacks: ShellCallbacks, onOpenStatus: () -> Unit) {
    if (callbacks.forceStopNotice && autoDetect.mode != AutoDetectMode.Off) {
        ForceStopNotice(rearmed = autoDetect.mode == AutoDetectMode.Ready, onDismiss = callbacks.onDismissForceStop)
    }
    when (autoDetect.mode) {
        AutoDetectMode.NeedsAttention -> autoDetect.reasons.firstOrNull()?.let { AttentionBanner(it, onFix = onOpenStatus) }
        AutoDetectMode.Off -> AutoDetectNotice(onTurnOn = callbacks.onOpenRecordingSettings)
        AutoDetectMode.Ready -> Unit
    }
}

/** A pushed screen with a back arrow (Profile, All drives). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(DpIcons.Back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}
