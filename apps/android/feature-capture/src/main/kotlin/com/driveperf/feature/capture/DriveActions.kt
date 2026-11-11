package com.driveperf.feature.capture

import androidx.compose.ui.unit.dp

/** The Stop confirmation (S-DRV-02). */
class StopActions(
    val onRequested: () -> Unit,
    val onDismissed: () -> Unit,
    val onConfirmed: () -> Unit,
)

/** The passenger escape (S-DRV-03). */
class PassengerActions(
    val onRequested: () -> Unit,
    val onDismissed: () -> Unit,
    val onKept: () -> Unit,
    val onDiscardRequested: () -> Unit,
    val onDiscardConfirmed: () -> Unit,
)

/** Every control the in-drive screens can fire. The screens hold no state of their own. */
class InDriveActions(
    val stop: StopActions,
    val passenger: PassengerActions,
    val onCoverDismissed: () -> Unit = {},
)

/** Touch targets on the drive screens are at least 64 dp (F-CAP-07.5); the big buttons are 96 dp. */
internal val DriveButtonHeight = 96.dp
internal val DriveTouchMin = 64.dp
