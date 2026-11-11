package com.driveperf.feature.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.delay

/** How long the "Automatic trips are on" confirmation shows before the flow moves on by itself (S-ONB-09). */
internal const val ALL_THE_TIME_ADVANCE_MS = 1_000L

/**
 * Hosts onboarding: launches the system permission requests, Settings and the in-app browser, re-reads permission state
 * whenever the app resumes (so the result of the "Allow all the time" Settings step is seen straight away, F-ONB-04.4),
 * and reports completion upward. The consent choices are committed by the view model at "Continue" on S-ONB-13.
 */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel,
    permissionReader: PermissionReader,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onLocationResult(permissionReader.read())
    }
    val preciseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPreciseResult(permissionReader.read())
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onBackgroundRuntimeResult(permissionReader.read())
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onMotionResult(permissionReader.read())
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationsResult(permissionReader.read())
    }

    // The user may grant a permission in Settings and come back: read it the moment the app resumes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onPermissionsChanged(permissionReader.read()) }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    LaunchedEffect(state.allTheTime) {
        if (state.allTheTime == AllTheTimeResult.Granted) {
            delay(ALL_THE_TIME_ADVANCE_MS)
            viewModel.onContinue()
        }
    }
    BackHandler(enabled = state.canGoBack || state.sheet != null) {
        if (state.sheet != null) viewModel.onDismissSheet() else viewModel.onBack()
    }

    val launchers = PermissionLaunchers(
        location = { locationLauncher.launch(LOCATION_PERMISSIONS) },
        precise = { preciseLauncher.launch(LOCATION_PERMISSIONS) },
        background = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
        activity = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        notifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
    )
    val actions = remember(viewModel) { onboardingActions(viewModel, context, launchers) }
    OnboardingScreen(state = state, actions = actions, modifier = modifier)
}

/** The system permission requests the route launches, as plain functions so [onboardingActions] stays small. */
private class PermissionLaunchers(
    val location: () -> Unit,
    val precise: () -> Unit,
    val background: () -> Unit,
    val activity: () -> Unit,
    val notifications: () -> Unit,
)

private fun onboardingActions(viewModel: OnboardingViewModel, context: Context, launch: PermissionLaunchers) = OnboardingActions(
    onContinue = viewModel::onContinue,
    onBack = viewModel::onBack,
    onSkipIntro = viewModel::onSkipIntro,
    onAgeConfirmedChanged = viewModel::onAgeConfirmedChanged,
    onAgeContinue = viewModel::onAgeContinue,
    onUnder18 = viewModel::onUnder18,
    onUnder18Undone = viewModel::onUnder18Undone,
    onOpenTerms = { InAppBrowser.open(context, OnboardingLinks.TERMS) },
    onOpenPrivacy = { InAppBrowser.open(context, OnboardingLinks.PRIVACY) },
    onShowHowItWorks = viewModel::onShowHowItWorks,
    onOpenHowItRecords = { InAppBrowser.open(context, OnboardingLinks.HOW_IT_RECORDS) },
    onDismissSheet = viewModel::onDismissSheet,
    onRequestLocation = launch.location,
    onLocationNotNow = viewModel::onLocationNotNow,
    onRequestPrecise = launch.precise,
    onKeepApproximate = viewModel::onKeepApproximate,
    onPreciseContinue = viewModel::onPreciseContinue,
    onContinueWithoutLocation = viewModel::onContinueWithoutLocation,
    onOpenAppSettings = { context.startActivity(GuideLauncher.appDetails(context)) },
    onRequestActivity = launch.activity,
    onAutoTripsToggled = viewModel::onAutoTripsToggled,
    onAutoTripsContinue = {
        if (viewModel.needsRuntimeBackgroundRequest) {
            // Android 10 offers background location in its runtime dialog, so there is no Settings step.
            launch.background()
        } else {
            viewModel.onAutoTripsContinue()
        }
    },
    onOpenLocationSettings = {
        viewModel.onSettingsOpened()
        context.startActivity(GuideLauncher.appDetails(context))
    },
    onContinueInManualMode = viewModel::onContinueInManualMode,
    onRequestNotifications = launch.notifications,
    onOpenGuideStep = { step -> GuideLauncher.open(context, step) },
    onBatteryDone = viewModel::onBatteryDone,
    onBatteryRemindLater = viewModel::onBatteryRemindLater,
    onBatteryShowSteps = viewModel::onBatteryShowSteps,
    onCrashReportsChanged = viewModel::onCrashReportsChanged,
    onUsageStatisticsChanged = viewModel::onUsageStatisticsChanged,
    onHelpContinue = viewModel::onHelpContinue,
)

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
