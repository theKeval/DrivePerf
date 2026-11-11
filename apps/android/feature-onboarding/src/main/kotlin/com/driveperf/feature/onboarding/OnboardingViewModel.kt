package com.driveperf.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The two separate privacy choices of S-ONB-13. Both are off until the user turns them on (F-ONB-08.1). */
data class ConsentChoices(val crashReports: Boolean = false, val usageStatistics: Boolean = false)

/** The result of the S-ONB-09 settings step, read when the app comes back (within a second, on resume). */
enum class AllTheTimeResult { Waiting, Granted, NotGranted }

/** Everything the onboarding screens draw. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val permissions: PermissionState = PermissionState(),
    /** The step whose system request was just refused, so the page can explain and offer Settings. */
    val deniedStep: OnboardingStep? = null,
    val sheet: OnboardingSheet? = null,
    /** S-ONB-04: never pre-ticked. */
    val ageConfirmed: Boolean = false,
    val under18Blocked: Boolean = false,
    /** S-ONB-08: the toggle. Off until the user turns it on. */
    val autoTripsOn: Boolean = false,
    /** S-ONB-08 declined banner: the user asked for automatic trips but it could not be turned on. */
    val autoTripsDeclined: Boolean = false,
    val allTheTime: AllTheTimeResult = AllTheTimeResult.Waiting,
    /** S-ONB-11 refused: the user kept approximate location, so trips record as "route only". */
    val preciseRefused: Boolean = false,
    val oemGuide: OemGuide? = null,
    /** S-ONB-12 remindLater state. */
    val batteryReminded: Boolean = false,
    val crashReports: Boolean = false,
    val usageStatistics: Boolean = false,
    val finished: Boolean = false,
    val setup: SetupPosition? = null,
    val progress: Float = 0f,
    /** The pages visited, so Back returns to the page the user came from, skipped pages included. */
    val history: List<OnboardingStep> = emptyList(),
) {
    val canGoBack: Boolean get() = history.isNotEmpty() && !under18Blocked

    /**
     * Automatic trips will really run: the opt-in **and** every permission the wake path needs (the same rule as
     * `WakePath.automaticAllowed`). The Ready page tells the truth from this, not from the toggle alone.
     */
    val automaticEffective: Boolean
        get() = autoTripsOn && permissions.fineLocation && permissions.backgroundLocation && permissions.activityRecognition

    /** The user asked for automatic trips but a permission is still missing, so nothing will start on its own yet. */
    val automaticWaitingForPermission: Boolean get() = autoTripsOn && !automaticEffective
}

/**
 * State holder for onboarding (a platform-only flow, so a native view model, ADR-002 section 4).
 *
 * Consent switches start **off** and are never pre-selected (ADR-007, F-ONB-08), and they are committed only when the
 * user continues from S-ONB-13 ([onConsentCommitted]): nothing is sent before then. Automatic trips are off until the
 * user turns them on (F-ONB-04.2).
 */
@Suppress("TooManyFunctions") // one handler per button on the thirteen pages and four sheets
class OnboardingViewModel(
    initialPermissions: PermissionState,
    private val settings: OnboardingSettings,
    private val oemGuide: OemGuide? = null,
    initialChoices: ConsentChoices = ConsentChoices(),
    private val onConsentCommitted: (ConsentChoices) -> Unit = {},
    private val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        OnboardingUiState(
            permissions = initialPermissions,
            under18Blocked = settings.under18Blocked,
            // A phone that was already blocked reopens on the blocked page, with nothing else reachable.
            step = if (settings.under18Blocked) OnboardingStep.AgeTerms else OnboardingStep.Welcome,
            crashReports = initialChoices.crashReports,
            usageStatistics = initialChoices.usageStatistics,
            oemGuide = oemGuide,
        ).withPosition(sdkInt),
    )
    val state: StateFlow<OnboardingUiState> = mutable.asStateFlow()

    /** True after the user opened Settings from S-ONB-09, so the next resume is the return the page is waiting for. */
    private var awaitingSettingsReturn = false

    // region navigation

    fun onContinue() = mutable.update { it.advance() }

    /** "Skip" on an intro page goes to the age step, which cannot be skipped (F-ONB-01.3). */
    fun onSkipIntro() = mutable.update { it.goTo(OnboardingStep.AgeTerms) }

    fun onBack() {
        mutable.update { current ->
            if (current.under18Blocked) return@update current // onboarding stops at the blocked page
            val previous = current.history.lastOrNull() ?: return@update current
            current.copy(
                step = previous, history = current.history.dropLast(1), deniedStep = null, sheet = null,
                allTheTime = AllTheTimeResult.Waiting,
            ).withPosition(sdkInt)
        }
    }

    // endregion

    // region S-ONB-04 age and terms

    fun onAgeConfirmedChanged(value: Boolean) = mutable.update { it.copy(ageConfirmed = value) }

    /** "Continue": allowed only when the box is ticked. Saves the accepted version and date on this phone. */
    fun onAgeContinue() {
        if (!mutable.value.ageConfirmed) return
        settings.recordTermsAccepted(TERMS_VERSION)
        mutable.update { it.advance() }
    }

    /** "I'm under 18": only a local flag is stored (F-ONB-02.2). */
    fun onUnder18() {
        settings.under18Blocked = true
        mutable.update { it.copy(under18Blocked = true, ageConfirmed = false) }
    }

    fun onUnder18Undone() {
        settings.under18Blocked = false
        mutable.update { it.copy(under18Blocked = false) }
    }

    // endregion

    // region S-ONB-05 / sheets

    fun onShowHowItWorks() = mutable.update { it.copy(sheet = OnboardingSheet.HowItWorks) }

    /**
     * The sheet was closed (scrim, swipe, Back or its own button). Dismissing the precise-location sheet after some
     * location was granted goes on to motion: location is on, just approximate (the same as "Continue", S-ONB-11).
     */
    fun onDismissSheet() = mutable.update { current ->
        if (current.sheet == OnboardingSheet.PreciseLocation && current.step == OnboardingStep.Location &&
            current.permissions.hasLocation
        ) {
            current.copy(sheet = null).advance()
        } else {
            current.copy(sheet = null)
        }
    }

    // endregion

    // region permissions

    /** The location dialog (S-ONB-06) came back. Precise → on; approximate only → S-ONB-11; refused → S-ONB-16. */
    fun onLocationResult(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            when {
                permissions.fineLocation -> updated.advance()
                permissions.approximateOnly -> updated.copy(sheet = OnboardingSheet.PreciseLocation, deniedStep = null)
                else -> updated.copy(deniedStep = OnboardingStep.Location, sheet = OnboardingSheet.LocationDenied)
            }
        }
    }

    /** "Not now" on the location primer: the dialog never shows (S-ONB-16, F-ONB-04). */
    fun onLocationNotNow() = mutable.update { it.copy(sheet = OnboardingSheet.LocationDenied) }

    /** S-ONB-16 "Not now" / "Continue without location": on to notifications, automatic trips off. */
    fun onContinueWithoutLocation() {
        settings.automaticTripsEnabled = false
        mutable.update { it.copy(sheet = null, autoTripsOn = false, deniedStep = null).advance() }
    }

    /** S-ONB-11 primary: the route asked for fine location again. */
    fun onPreciseResult(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            if (permissions.fineLocation) updated.copy(sheet = null).advance() else updated.copy(preciseRefused = true)
        }
    }

    /** S-ONB-11 "Keep approximate": trips record as "route only, no event placement" (F-ONB-04.5). */
    fun onKeepApproximate() = mutable.update { it.copy(preciseRefused = true) }

    /** S-ONB-11 refused state "Continue": the sheet closes and the flow goes on to motion. */
    fun onPreciseContinue() = mutable.update { it.copy(sheet = null).advance() }

    /** The activity dialog (S-ONB-07) came back. Granted → on; refused → the page explains, Continue still works. */
    fun onMotionResult(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            if (permissions.activityRecognition) updated.advance() else updated.copy(deniedStep = OnboardingStep.Motion)
        }
    }

    /** The notification dialog (S-ONB-10) came back. */
    fun onNotificationsResult(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            if (permissions.notifications) updated.advance() else updated.copy(deniedStep = OnboardingStep.Notifications)
        }
    }

    // endregion

    // region S-ONB-08 / S-ONB-09 automatic trips

    fun onAutoTripsToggled(on: Boolean) = mutable.update { it.copy(autoTripsOn = on, autoTripsDeclined = false) }

    /**
     * Android 10 only: its runtime dialog offers background location, so S-ONB-08 "Continue" asks for it directly
     * (S-ONB-09 is skipped there). The route checks this on "Continue": if true it launches the request
     * and reports back through [onBackgroundRuntimeResult], otherwise it calls [onAutoTripsContinue].
     */
    val needsRuntimeBackgroundRequest: Boolean
        get() = mutable.value.let {
            it.step == OnboardingStep.AutoTrips && it.autoTripsOn && it.permissions.fineLocation &&
                !it.permissions.backgroundLocation && sdkInt < android.os.Build.VERSION_CODES.R
        }

    /**
     * S-ONB-08 "Continue": the user's affirmative action. Off → manual trips. On → the opt-in is saved, and Android 11
     * and later go to the "Allow all the time" settings step.
     */
    fun onAutoTripsContinue() {
        val on = mutable.value.autoTripsOn
        settings.automaticTripsEnabled = on
        mutable.update { it.advance() }
    }

    /** Android 10: the runtime dialog for background location came back. */
    fun onBackgroundRuntimeResult(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            if (permissions.backgroundLocation) {
                settings.automaticTripsEnabled = true
                updated.advance()
            } else {
                updated.declineAutomaticTrips()
            }
        }
    }

    /** S-ONB-09: the user went to Settings, so the next resume is the return to read (F-ONB-04.4). */
    fun onSettingsOpened() {
        awaitingSettingsReturn = true
    }

    /** S-ONB-09 "Continue with manual trips" and S-ONB-16 "Continue in manual mode". */
    fun onContinueInManualMode() {
        awaitingSettingsReturn = false
        settings.automaticTripsEnabled = false
        mutable.update { it.copy(autoTripsOn = false, sheet = null, allTheTime = AllTheTimeResult.Waiting).advance() }
    }

    // endregion

    // region S-ONB-12 battery

    fun onBatteryDone() {
        settings.recordReliabilityGuide(ReliabilityGuideState.Done)
        mutable.update { it.copy(batteryReminded = false).advance() }
    }

    fun onBatteryRemindLater() {
        settings.recordReliabilityGuide(ReliabilityGuideState.RemindLater)
        mutable.update { it.copy(batteryReminded = true) }
    }

    fun onBatteryShowSteps() = mutable.update { it.copy(batteryReminded = false) }

    // endregion

    // region S-ONB-13 help improve

    fun onCrashReportsChanged(value: Boolean) = mutable.update { it.copy(crashReports = value) }

    fun onUsageStatisticsChanged(value: Boolean) = mutable.update { it.copy(usageStatistics = value) }

    /**
     * "Continue": saves both choices exactly as set (nothing pre-selected). The app applies them: crash reports start or
     * stay off, and the analytics gate sends what it held if usage is on or drops it if not (F-ONB-08.2).
     */
    fun onHelpContinue() {
        val current = mutable.value
        onConsentCommitted(ConsentChoices(current.crashReports, current.usageStatistics))
        mutable.update { it.advance() }
    }

    // endregion

    /**
     * Called after any system permission result and whenever the app resumes (the user may have used Settings). On the
     * "Allow all the time" page it reads the result of the Settings round trip; on a permission page whose permission
     * is now granted the flow moves on by itself.
     */
    fun onPermissionsChanged(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            when {
                current.step == OnboardingStep.AllTheTime && (awaitingSettingsReturn || permissions.backgroundLocation) -> {
                    awaitingSettingsReturn = false
                    updated.copy(allTheTime = if (permissions.backgroundLocation) AllTheTimeResult.Granted else AllTheTimeResult.NotGranted)
                }
                current.step.grantedBy(permissions) -> updated.advance()
                else -> updated.withPosition(sdkInt)
            }
        }
    }

    // region helpers

    private fun OnboardingUiState.context() = FlowContext(permissions, autoTripsOn, oemGuide, sdkInt)

    private fun OnboardingUiState.withPosition(sdk: Int): OnboardingUiState {
        val context = FlowContext(permissions, autoTripsOn, oemGuide, sdk)
        val shown = OnboardingFlow.shown(context)
        return copy(
            setup = OnboardingFlow.setupPosition(step, context),
            progress = (shown.indexOf(step) + 1).coerceAtLeast(1).toFloat() / shown.size,
        )
    }

    private fun OnboardingUiState.advance(): OnboardingUiState {
        val next = OnboardingFlow.next(step, context())
        return if (next == null) copy(finished = true) else goTo(next)
    }

    private fun OnboardingUiState.goTo(target: OnboardingStep): OnboardingUiState {
        return copy(
            step = target, history = history + step, deniedStep = null, sheet = null, allTheTime = AllTheTimeResult.Waiting,
        ).withPosition(sdkInt)
    }

    /** A refused request for automatic trips (Android 10): manual mode, with the banner and the S-ONB-16 sheet. */
    private fun OnboardingUiState.declineAutomaticTrips(): OnboardingUiState {
        settings.automaticTripsEnabled = false
        return copy(autoTripsOn = false, autoTripsDeclined = true, sheet = OnboardingSheet.ManualMode)
    }

    private fun OnboardingStep.grantedBy(p: PermissionState) = when (this) {
        OnboardingStep.Location -> p.fineLocation
        OnboardingStep.Motion -> p.activityRecognition
        OnboardingStep.Notifications -> p.notifications
        else -> false
    }

    // endregion
}
