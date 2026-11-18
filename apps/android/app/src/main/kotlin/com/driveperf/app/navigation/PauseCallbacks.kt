package com.driveperf.app.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.driveperf.app.core.WakePath
import com.driveperf.domain.capture.AutoDetectPauseLength
import com.driveperf.feature.capture.AutoDetectMode
import com.driveperf.feature.capture.AutoDetectStatus
import com.driveperf.feature.capture.PauseAutoDetectSheet
import com.driveperf.feature.capture.PauseSheetActions
import com.driveperf.feature.capture.R as CaptureR

/** Pause, resume and undo for automatic trips (F-CAP-15), as the shell and Recording settings call them. */
class PauseCallbacks(
    val onPause: (AutoDetectPauseLength, Long?) -> Unit,
    val onResume: () -> Unit,
    val onUndo: () -> Unit,
)

/** [PauseCallbacks] on the core through [wakePath]. Resume says "Auto-detect resumed" (S-HOME-03 toast). */
@Composable
fun rememberPauseCallbacks(wakePath: WakePath): PauseCallbacks {
    val context = LocalContext.current.applicationContext
    return remember(wakePath, context) {
        PauseCallbacks(
            onPause = wakePath::pauseAutoDetect,
            onResume = {
                wakePath.resumeAutoDetect()
                Toast.makeText(context, CaptureR.string.pause_resumed_toast, Toast.LENGTH_SHORT).show()
            },
            onUndo = wakePath::undoPauseAutoDetect,
        )
    }
}

/** S-HOME-03 for [status]: the choices while not paused, Resume / Keep paused while paused. */
@Composable
fun PauseSheet(status: AutoDetectStatus, pause: PauseCallbacks, onDismiss: () -> Unit) {
    PauseAutoDetectSheet(
        paused = status.mode == AutoDetectMode.Paused,
        pausedUntilMs = status.pausedUntilMs,
        actions = PauseSheetActions(
            onDismiss = onDismiss,
            onPause = pause.onPause,
            onUndo = pause.onUndo,
            onResume = {
                pause.onResume()
                onDismiss()
            },
        ),
    )
}
