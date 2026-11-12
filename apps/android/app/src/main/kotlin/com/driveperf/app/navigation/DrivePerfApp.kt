package com.driveperf.app.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.driveperf.app.BuildConfig
import com.driveperf.app.R
import com.driveperf.app.bridge.PlatformCaptureController
import com.driveperf.app.core.WakePath
import com.driveperf.di.DrivePerfCore
import com.driveperf.feature.account.ProfileScreen
import com.driveperf.feature.account.ProfileUiState
import com.driveperf.feature.capture.CaptureUiState
import com.driveperf.feature.capture.CaptureViewModel
import com.driveperf.feature.capture.DriveCover
import com.driveperf.feature.capture.DriveCoverHost
import com.driveperf.feature.capture.InDriveActions
import com.driveperf.feature.capture.PassengerActions
import com.driveperf.feature.capture.StopActions
import com.driveperf.feature.drives.AllDrivesScreen
import com.driveperf.feature.drives.DrivesHomeScreen
import com.driveperf.feature.drives.DrivesViewModel
import com.driveperf.feature.garage.GarageScreen
import com.driveperf.feature.insights.InsightsScreen
import com.driveperf.feature.onboarding.OnboardingRoute
import com.driveperf.feature.onboarding.OnboardingViewModel
import com.driveperf.feature.onboarding.PermissionReader
import com.driveperf.feature.routes.RoutesScreen
import com.driveperf.platform.crash.ConsentStore
import com.driveperf.platform.crash.CrashReporter
import com.driveperf.platform.crash.PrivacyChoices
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private val ToolbarClearance = 88.dp // the floating toolbar (64 dp) plus its gap, so lists scroll clear of it

/**
 * The app's navigation graph.
 *
 * Every navigation passes through the in-drive gate (screen-map.md section 2.2): while a drive is recording, the only
 * screen is the in-drive cover (S-DRV-01, or S-DRV-05 for held content, then S-DRV-04 after the trip); the rest of the
 * graph is not composed, and returns exactly where it was afterwards. Content that arrives while locked (a notification
 * or deep link) goes through `CaptureViewModel.requestContent`.
 */
@Composable
fun DrivePerfApp() {
    val captureViewModel: CaptureViewModel = koinViewModel()
    val capture by captureViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val cover by captureViewModel.cover.collectAsStateWithLifecycle()
    val actions = remember(captureViewModel) { captureViewModel.driveActions() }

    val current = cover
    if (current is DriveCover.InDrive) {
        val wakePath: WakePath = koinInject()
        LifecycleStartEffect(wakePath) {
            wakePath.setInDriveScreenVisible(true)
            onStopOrDispose { wakePath.setInDriveScreenVisible(false) }
        }
    }
    if (current != null) {
        DriveCoverHost(current, actions)
    } else {
        AppNavHost(navController, captureViewModel, capture)
    }
}

private fun CaptureViewModel.driveActions() = InDriveActions(
    stop = StopActions(::onStopRequested, ::onStopDismissed, ::onStopConfirmed),
    passenger = PassengerActions(
        onRequested = ::onPassengerRequested,
        onDismissed = ::onPassengerDismissed,
        onKept = ::onPassengerKept,
        onDiscardRequested = ::onPassengerDiscardRequested,
        onDiscardConfirmed = ::onPassengerDiscardConfirmed,
    ),
    onCoverDismissed = ::onCoverDismissed,
)

/** Everything the destinations share, gathered once so each destination stays small. */
private class AppEnv(
    val navController: NavHostController,
    val captureViewModel: CaptureViewModel,
    val capture: CaptureUiState,
    val services: AppServices,
    val fixLocation: () -> Unit,
    val fixAutoDetect: () -> Unit,
)

/** The app-level singletons the destinations talk to. */
private class AppServices(
    val consent: ConsentStore,
    val crashReporter: CrashReporter,
    val privacy: PrivacyChoices,
    val captureController: PlatformCaptureController,
    val permissionReader: PermissionReader,
)

@Composable
private fun AppNavHost(navController: NavHostController, captureViewModel: CaptureViewModel, capture: CaptureUiState) {
    val consent: ConsentStore = koinInject()
    val captureController: PlatformCaptureController = koinInject()
    val permissionReader: PermissionReader = koinInject()
    val context = LocalContext.current
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        captureController.refreshPermissions()
        // Refused again (or "don't ask again"): the system dialog will not show, so send the user to Settings.
        if (!permissionReader.read().fineLocation) context.startActivity(appSettingsIntent(context.packageName))
    }
    val services = AppServices(consent, koinInject(), koinInject(), captureController, permissionReader)
    val env = AppEnv(
        navController, captureViewModel, capture, services,
        fixLocation = { requestLocation.launch(LOCATION_PERMISSIONS) },
        fixAutoDetect = { context.startActivity(appSettingsIntent(context.packageName)) },
    )
    val start = if (consent.onboardingCompleted) Tab.Drives.route else Routes.ONBOARDING
    NavHost(navController = navController, startDestination = start) {
        onboardingDestination(env)
        tabDestinations(env)
        allDrivesDestination(env)
        profileDestination(env)
    }
}

private fun NavGraphBuilder.onboardingDestination(env: AppEnv) {
    composable(Routes.ONBOARDING) {
        val viewModel: OnboardingViewModel = koinViewModel()
        OnboardingRoute(
            viewModel = viewModel,
            permissionReader = env.services.permissionReader,
            onFinished = {
                env.services.consent.onboardingCompleted = true
                env.services.captureController.refreshPermissions()
                env.navController.navigate(Tab.Drives.route) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            },
        )
    }
}

private fun NavGraphBuilder.tabDestinations(env: AppEnv) {
    val callbacks = ShellCallbacks(
        onTabSelected = env.navController::navigateToTab,
        onProfile = { env.navController.navigate(Routes.PROFILE) },
        onStart = env.captureViewModel::onStart,
        onOpenDrive = {}, // while recording, the in-drive gate already shows the drive screen
        onFixLocation = env.fixLocation,
        onFixAutoDetect = env.fixAutoDetect,
    )
    for (tab in Tab.entries) {
        composable(tab.route) {
            val autoDetectOff = rememberAutoDetectOff(env.services.permissionReader, env.services.consent)
            val wakePath = if (tab == Tab.Drives) koinInject<WakePath>() else null
            val wakeStatus = wakePath?.status?.collectAsStateWithLifecycle()?.value
            TabScaffold(
                selected = tab,
                capture = env.capture,
                callbacks = callbacks.copy(
                    autoDetectOff = autoDetectOff,
                    wakeStatus = wakeStatus,
                    onDismissForceStop = { wakePath?.dismissForceStopNotice() },
                ),
            ) { padding ->
                TabContent(tab, padding, onSeeAll = { env.navController.navigate(Routes.ALL_DRIVES) })
            }
        }
    }
}

private fun NavGraphBuilder.allDrivesDestination(env: AppEnv) {
    composable(Routes.ALL_DRIVES) {
        val viewModel: DrivesViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        DetailScaffold(title = stringResource(R.string.all_drives_title), onBack = { env.navController.popBackStack() }) { padding ->
            AllDrivesScreen(state = state, modifier = Modifier.padding(padding), onAction = viewModel::onAction)
        }
    }
}

private fun NavGraphBuilder.profileDestination(env: AppEnv) {
    composable(Routes.PROFILE) {
        var crash by remember { mutableStateOf(env.services.consent.crashReportsAllowed) }
        var stats by remember { mutableStateOf(env.services.consent.usageStatisticsAllowed) }
        DetailScaffold(title = stringResource(R.string.profile_title), onBack = { env.navController.popBackStack() }) { padding ->
            ProfileScreen(
                state = ProfileUiState(
                    usageStatistics = stats,
                    crashReports = crash,
                    crashReportingAvailable = env.services.crashReporter.isAvailable,
                    versionName = BuildConfig.VERSION_NAME,
                    coreDescription = stringResource(R.string.core_line, DrivePerfCore.hello().subtitle),
                ),
                onUsageStatisticsChanged = {
                    stats = it
                    env.services.privacy.setUsageStatistics(it)
                },
                onCrashReportsChanged = {
                    crash = it
                    env.services.privacy.setCrashReports(it)
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TabContent(tab: Tab, padding: PaddingValues, onSeeAll: () -> Unit) {
    val modifier = Modifier.padding(top = padding.calculateTopPadding())
    when (tab) {
        Tab.Drives -> {
            val viewModel: DrivesViewModel = koinViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            DrivesHomeScreen(
                state = state,
                onSeeAll = onSeeAll,
                modifier = modifier,
                bottomPadding = padding.calculateBottomPadding() + ToolbarClearance,
                onAction = viewModel::onAction,
            )
        }
        Tab.Routes -> RoutesScreen(modifier)
        Tab.Insights -> InsightsScreen(modifier)
        Tab.Garage -> GarageScreen(modifier)
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(Tab.Drives.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
