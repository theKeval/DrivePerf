package com.driveperf.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.core.WakePath
import com.driveperf.app.core.forCapture
import com.driveperf.feature.capture.AttentionReason
import com.driveperf.feature.capture.AutoDetectMode
import com.driveperf.feature.capture.AutoDetectReadiness
import com.driveperf.feature.capture.AutoDetectStatus
import com.driveperf.feature.capture.CapturePermissions
import com.driveperf.feature.capture.CaptureStatusActions
import com.driveperf.feature.capture.CaptureStatusSheet
import com.driveperf.feature.capture.autoDetectDetail
import com.driveperf.feature.capture.health
import com.driveperf.feature.capture.label
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.feature.settings.RecordingSettingsScreen
import com.driveperf.feature.settings.RecordingSettingsUiState
import com.driveperf.feature.settings.RecordingStatusLine
import com.driveperf.platform.crash.ConsentSettings

/**
 * Turning automatic trips on from Recording settings (F-SET-01 AC3): walks the missing permissions one at a time with the
 * onboarding steps, each at most once per attempt, so a refusal never loops. What is still missing afterwards shows as
 * "Needs attention", with Fix in the status sheet.
 */
class EnableAutoTrips {
    private val attempted = mutableSetOf<AttentionReason>()
    private var active = false

    /** Starts the next step; set by the route to its [AutoDetectFixer]. */
    var fix: (AttentionReason) -> Unit = {}

    fun begin(permissions: CapturePermissions) {
        attempted.clear()
        active = true
        continueWith(permissions)
    }

    fun cancel() {
        active = false
    }

    /** After each step: the next reason Settings can fix and this attempt has not tried yet, or done. */
    fun continueWith(permissions: CapturePermissions) {
        if (!active) return
        val next = nextReason(permissions, attempted)
        if (next == null) {
            active = false
        } else {
            attempted += next
            fix(next)
        }
    }

    companion object {
        fun nextReason(permissions: CapturePermissions, attempted: Set<AttentionReason>): AttentionReason? =
            AutoDetectReadiness.reasons(permissions).firstOrNull { it.fix != null && it !in attempted }
    }
}

/** What S-SET-03 needs from the app. */
class RecordingSettingsDeps(
    val wakePath: WakePath,
    val consent: ConsentSettings,
    val captureController: PlatformCaptureController,
    val permissionReader: PermissionReader,
)

/**
 * S-SET-03 Recording settings. The switch saves the opt-in and calls [WakePath.refresh] through the capture controller at
 * once, so turning it off reaches the core (`setAutoDetect(false)`, which removes the watchdog) before the user leaves the
 * screen. Turning it on asks for whatever is missing ([EnableAutoTrips]).
 */
@Composable
fun RecordingSettingsRoute(deps: RecordingSettingsDeps, modifier: Modifier = Modifier) {
    val status by deps.wakePath.autoDetect.collectAsStateWithLifecycle()
    var optedIn by remember { mutableStateOf(deps.consent.automaticTripsEnabled) }
    var showStatus by rememberSaveable { mutableStateOf(false) }
    val session = remember { EnableAutoTrips() }
    val fixer = rememberAutoDetectFixer(
        reader = deps.permissionReader,
        onPermissionsChanged = deps.captureController::refreshPermissions,
        onFinished = { session.continueWith(deps.permissionReader.read().forCapture()) },
    )
    session.fix = fixer::fix
    AllTheTimeDialog(fixer)

    RecordingSettingsScreen(
        state = RecordingSettingsUiState(automaticTrips = optedIn, status = statusLine(status)),
        onAutomaticTripsChanged = { on ->
            optedIn = on
            deps.consent.automaticTripsEnabled = on
            deps.captureController.refreshPermissions() // WakePath.refresh: the core follows at once
            if (on) session.begin(deps.permissionReader.read().forCapture()) else session.cancel()
        },
        onStatusClick = { showStatus = true },
        modifier = modifier,
    )
    if (showStatus) {
        CaptureStatusSheet(
            status = status,
            actions = CaptureStatusActions(
                onDismiss = { showStatus = false },
                onFix = fixer::fix,
                onOpenRecordingSettings = { showStatus = false }, // already here
            ),
        )
    }
}

@Composable
private fun statusLine(status: AutoDetectStatus): RecordingStatusLine? {
    if (status.mode == AutoDetectMode.Off) return null
    val health = status.mode.health()
    return RecordingStatusLine(
        health = health,
        healthLabel = health.label(),
        title = stringResource(status.mode.label),
        detail = autoDetectDetail(status),
    )
}
