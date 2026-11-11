package com.driveperf.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Hosts onboarding: launches the system permission requests, re-reads permission state when the app resumes, and reports
 * consent choices and completion upward. The app decides what to persist.
 */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel,
    permissionReader: PermissionReader,
    onConsentChanged: (crashReports: Boolean, usageStatistics: Boolean) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun afterRequest(step: OnboardingStep) {
        val permissions = permissionReader.read()
        viewModel.onPermissionsChanged(permissions)
        val granted = when (step) {
            OnboardingStep.Location -> permissions.fineLocation
            OnboardingStep.BackgroundLocation -> permissions.backgroundLocation
            OnboardingStep.ActivityRecognition -> permissions.activityRecognition
            OnboardingStep.Notifications -> permissions.notifications
            else -> true
        }
        if (!granted) viewModel.onPermissionDenied(step)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterRequest(OnboardingStep.Location)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterRequest(OnboardingStep.BackgroundLocation)
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterRequest(OnboardingStep.ActivityRecognition)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterRequest(OnboardingStep.Notifications)
    }

    // The user may grant a permission in Settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onPermissionsChanged(permissionReader.read()) }
    LaunchedEffect(state.crashReports, state.usageStatistics) { onConsentChanged(state.crashReports, state.usageStatistics) }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    val actions = remember(viewModel) {
        OnboardingActions(
            onContinue = viewModel::onContinue,
            onBack = viewModel::onBack,
            onRequestLocation = {
                locationLauncher.launch(LOCATION_PERMISSIONS)
            },
            onRequestBackground = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            onRequestActivity = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenAppSettings = { context.startActivity(appSettingsIntent(context)) },
            onOpenBatterySettings = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            onCrashReportsChanged = viewModel::onCrashReportsChanged,
            onUsageStatisticsChanged = viewModel::onUsageStatisticsChanged,
        )
    }
    OnboardingScreen(state = state, actions = actions, modifier = modifier)
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
