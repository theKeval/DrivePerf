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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.driveperf.app.R
import com.driveperf.designsystem.DpAvatarButton
import com.driveperf.designsystem.DpIcons
import com.driveperf.feature.capture.CaptureToolbar
import com.driveperf.feature.capture.CaptureUiState

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
                    onStart = callbacks.onStart,
                    onOpenDrive = callbacks.onOpenDrive,
                    onFixLocation = callbacks.onFixLocation,
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        content = { padding ->
            val autoDetectOff = callbacks.autoDetectOff
            if (selected == Tab.Drives && autoDetectOff != null) {
                // F-ONB-04.6: Home says when automatic trips are off, above the tab's own content.
                Column(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
                    AutoDetectNotice(autoDetectOff, onFix = callbacks.onFixAutoDetect)
                    Box(modifier = Modifier.weight(1f)) { content(PaddingValues(bottom = padding.calculateBottomPadding())) }
                }
            } else {
                content(padding)
            }
        },
    )
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
