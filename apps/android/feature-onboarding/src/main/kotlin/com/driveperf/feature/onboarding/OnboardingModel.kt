package com.driveperf.feature.onboarding

import android.os.Build

/** Onboarding pages in order. Permission pages are separate steps on purpose: each gets its own explanation. */
enum class OnboardingStep {
    Welcome,
    HowItWorks,
    Location,
    BackgroundLocation,
    ActivityRecognition,
    Notifications,
    Battery,
    HelpImprove,
    Ready,
}

/** What the user has granted. Read from the system, never remembered, because it can change in Settings at any time. */
data class PermissionState(
    val fineLocation: Boolean = false,
    /** True when only approximate location was granted (S-ONB-11 asks for precise). */
    val approximateOnly: Boolean = false,
    val backgroundLocation: Boolean = false,
    val activityRecognition: Boolean = false,
    val notifications: Boolean = false,
)

/** The page order and which pages are skipped. Pure, so the ordering rules are unit-tested. */
object OnboardingFlow {
    private val order = OnboardingStep.entries

    /**
     * The page after [step], skipping pages that cannot or need not be shown:
     * - without fine location nothing else can work, and the system will not even offer background location, so the
     *   permission pages after Location are skipped;
     * - a permission that is already granted is not asked for again;
     * - notifications need no runtime permission below Android 13.
     */
    fun next(step: OnboardingStep, permissions: PermissionState, sdkInt: Int = Build.VERSION.SDK_INT): OnboardingStep? {
        var index = order.indexOf(step) + 1
        while (index < order.size) {
            val candidate = order[index]
            if (!skip(candidate, permissions, sdkInt)) return candidate
            index++
        }
        return null
    }

    fun previous(step: OnboardingStep, permissions: PermissionState, sdkInt: Int = Build.VERSION.SDK_INT): OnboardingStep? {
        var index = order.indexOf(step) - 1
        while (index >= 0) {
            val candidate = order[index]
            if (!skip(candidate, permissions, sdkInt)) return candidate
            index--
        }
        return null
    }

    /** Fraction of the flow completed, for the progress bar. Counts only pages that are shown. */
    fun progress(step: OnboardingStep, permissions: PermissionState, sdkInt: Int = Build.VERSION.SDK_INT): Float {
        val shown = order.filter { it == step || !skip(it, permissions, sdkInt) }
        return (shown.indexOf(step) + 1).toFloat() / shown.size
    }

    private fun skip(step: OnboardingStep, p: PermissionState, sdkInt: Int): Boolean = when (step) {
        OnboardingStep.Location -> p.fineLocation
        OnboardingStep.BackgroundLocation -> !p.fineLocation || p.backgroundLocation
        OnboardingStep.ActivityRecognition -> !p.fineLocation || p.activityRecognition
        OnboardingStep.Notifications -> !p.fineLocation || p.notifications || sdkInt < Build.VERSION_CODES.TIRAMISU
        OnboardingStep.Battery -> !p.fineLocation
        else -> false
    }
}
