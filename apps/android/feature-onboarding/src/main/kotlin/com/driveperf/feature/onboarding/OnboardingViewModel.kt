package com.driveperf.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the onboarding screens draw. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val permissions: PermissionState = PermissionState(),
    /** The step whose system request was just refused, so the page can explain and offer Settings. */
    val deniedStep: OnboardingStep? = null,
    val crashReports: Boolean = false,
    val usageStatistics: Boolean = false,
    val finished: Boolean = false,
    val progress: Float = 0f,
)

/**
 * State holder for onboarding (a platform-only flow, so a native view model, ADR-002 section 4).
 *
 * Consent switches start **off** and are never pre-selected (ADR-007, F-ONB-08).
 */
class OnboardingViewModel(
    initialPermissions: PermissionState,
    initialCrashReports: Boolean = false,
    initialUsageStatistics: Boolean = false,
    private val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        OnboardingUiState(
            permissions = initialPermissions,
            crashReports = initialCrashReports,
            usageStatistics = initialUsageStatistics,
            progress = OnboardingFlow.progress(OnboardingStep.Welcome, initialPermissions, sdkInt),
        ),
    )
    val state: StateFlow<OnboardingUiState> = mutable.asStateFlow()

    /**
     * Called after any system permission result and whenever the app resumes (the user may have used Settings). When the
     * page on screen is a permission page and its permission is now granted, the flow moves on by itself.
     */
    fun onPermissionsChanged(permissions: PermissionState) {
        mutable.update { current ->
            val updated = current.copy(permissions = permissions)
            if (current.step.isGrantedIn(permissions)) updated.advance() else updated.withProgress()
        }
    }

    /** The system dialog for [step] came back without the permission. */
    fun onPermissionDenied(step: OnboardingStep) {
        mutable.update { it.copy(deniedStep = step) }
    }

    fun onContinue() {
        mutable.update { it.advance() }
    }

    fun onBack() {
        mutable.update { current ->
            val previous = OnboardingFlow.previous(current.step, current.permissions, sdkInt)
            if (previous == null) current else current.copy(step = previous, deniedStep = null).withProgress()
        }
    }

    fun onCrashReportsChanged(value: Boolean) {
        mutable.update { it.copy(crashReports = value) }
    }

    fun onUsageStatisticsChanged(value: Boolean) {
        mutable.update { it.copy(usageStatistics = value) }
    }

    private fun OnboardingUiState.advance(): OnboardingUiState {
        val next = OnboardingFlow.next(step, permissions, sdkInt)
        return if (next == null) copy(finished = true) else copy(step = next, deniedStep = null).withProgress()
    }

    private fun OnboardingUiState.withProgress() = copy(progress = OnboardingFlow.progress(step, permissions, sdkInt))

    private fun OnboardingStep.isGrantedIn(p: PermissionState) = when (this) {
        OnboardingStep.Location -> p.fineLocation
        OnboardingStep.BackgroundLocation -> p.backgroundLocation
        OnboardingStep.ActivityRecognition -> p.activityRecognition
        OnboardingStep.Notifications -> p.notifications
        else -> false
    }
}
