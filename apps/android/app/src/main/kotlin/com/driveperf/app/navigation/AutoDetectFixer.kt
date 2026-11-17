package com.driveperf.app.navigation

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.driveperf.app.R
import com.driveperf.app.core.forCapture
import com.driveperf.feature.capture.AttentionFix
import com.driveperf.feature.capture.AttentionReason
import com.driveperf.feature.capture.AutoDetectReadiness
import com.driveperf.feature.onboarding.GuideLauncher
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.feature.onboarding.R as OnboardingR

/** How one attention reason is fixed on this phone. */
enum class FixStep {
    /** The system location dialog (fine and coarse): grants location, or upgrades approximate to precise. */
    LocationDialog,

    /** Android 10 offers "Allow all the time" in its runtime dialog. */
    BackgroundDialog,

    /** Android 11 and later grant background location only in Settings: explain (S-ONB-09 wording), then App info. */
    BackgroundSettings,

    /** The system physical activity dialog. */
    ActivityDialog,

    /** Nothing in Settings fixes it (Play services refused the activity registration). */
    None,
    ;

    companion object {
        /** The onboarding steps, reused (F-ONB-04): the system dialog while Android still shows it, else Settings. */
        fun of(reason: AttentionReason, sdkInt: Int): FixStep = when (reason.fix) {
            AttentionFix.Location -> LocationDialog
            AttentionFix.Background -> if (sdkInt < Build.VERSION_CODES.R) BackgroundDialog else BackgroundSettings
            AttentionFix.Activity -> ActivityDialog
            null -> None
        }

        /**
         * After a system dialog: when the permission is still missing and Android would not show the dialog again (the
         * user refused it twice, or chose "Don't allow" for good), App info is the only place left to grant it.
         */
        fun needsAppSettings(stillMissing: Boolean, systemWouldAskAgain: Boolean): Boolean = stillMissing && !systemWouldAskAgain
    }
}

/**
 * Fixes one [AttentionReason] at a time with the onboarding permission steps (F-ONB-04): the system dialog while Android
 * still shows it, otherwise DrivePerf's App info. [rememberAutoDetectFixer]'s `onFinished` runs once the user is back.
 * Render [AllTheTimeDialog] next to it: on Android 11+ "Allow all the time" is explained before App info opens.
 *
 * The open explanation and the App info step in progress are saved instance state, so a rotation or a process restart
 * while the user is in a dialog or in Settings does not lose the step (its result then still reaches `onFinished`).
 */
@Stable
class AutoDetectFixer internal constructor(explaining: MutableState<Boolean>) {
    internal var explainingBackground by explaining
    internal var start: (AttentionReason) -> Unit = {}
    internal var openBackgroundSettings: () -> Unit = {}
    internal var dismissBackground: () -> Unit = {}

    fun fix(reason: AttentionReason) = start(reason)
}

@Composable
fun rememberAutoDetectFixer(
    reader: PermissionReader,
    onPermissionsChanged: () -> Unit,
    onFinished: (AttentionReason) -> Unit = {},
): AutoDetectFixer {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val changed by rememberUpdatedState(onPermissionsChanged)
    val finished by rememberUpdatedState(onFinished)
    val explaining = rememberSaveable { mutableStateOf(false) }
    val fixer = remember(explaining) { AutoDetectFixer(explaining) }
    var awaitingSettings by rememberSaveable { mutableStateOf<AttentionReason?>(null) }

    fun openAppSettings(reason: AttentionReason) {
        awaitingSettings = reason
        context.startActivity(GuideLauncher.appDetails(context))
    }

    fun afterDialog(reason: AttentionReason, permission: String) {
        changed()
        val stillMissing = reason in AutoDetectReadiness.reasons(reader.read().forCapture())
        if (FixStep.needsAppSettings(stillMissing, activity.wouldAskAgain(permission))) openAppSettings(reason) else finished(reason)
    }

    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val reason = if (reader.read().hasLocation) AttentionReason.NoPreciseLocation else AttentionReason.NoLocation
        afterDialog(reason, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterDialog(AttentionReason.NoBackgroundLocation, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val motion = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterDialog(AttentionReason.NoActivityRecognition, Manifest.permission.ACTIVITY_RECOGNITION)
    }

    // Back from App info: the permission may have changed there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        awaitingSettings?.let {
            awaitingSettings = null
            changed()
            finished(it)
        }
    }

    fixer.start = { reason ->
        when (FixStep.of(reason, Build.VERSION.SDK_INT)) {
            FixStep.LocationDialog -> location.launch(LOCATION_PERMISSIONS)
            FixStep.BackgroundDialog -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            FixStep.ActivityDialog -> motion.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            FixStep.BackgroundSettings -> fixer.explainingBackground = true
            FixStep.None -> finished(reason)
        }
    }
    fixer.openBackgroundSettings = {
        fixer.explainingBackground = false
        openAppSettings(AttentionReason.NoBackgroundLocation)
    }
    fixer.dismissBackground = {
        fixer.explainingBackground = false
        finished(AttentionReason.NoBackgroundLocation)
    }
    return fixer
}

private fun Activity?.wouldAskAgain(permission: String): Boolean =
    this?.shouldShowRequestPermissionRationale(permission) ?: false

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun AllTheTimeDialog(fixer: AutoDetectFixer) {
    if (!fixer.explainingBackground) return
    AlertDialog(
        onDismissRequest = fixer.dismissBackground,
        title = { Text(stringResource(OnboardingR.string.onb_allday_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(OnboardingR.string.onb_auto_disclosure), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(OnboardingR.string.onb_allday_lead), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = fixer.openBackgroundSettings) { Text(stringResource(R.string.auto_detect_all_the_time_open)) }
        },
        dismissButton = {
            TextButton(onClick = fixer.dismissBackground) { Text(stringResource(R.string.auto_detect_all_the_time_not_now)) }
        },
    )
}
