package com.driveperf.feature.onboarding

import android.os.Build

/**
 * Onboarding pages in Android order (WP-A): intro, age and terms, the capture promise, then location, activity,
 * automatic trips (and the separate "Allow all the time" step), notifications, battery, the analytics choice and Ready.
 * Each permission is its own page on purpose: each gets its own explanation (F-ONB-04).
 */
enum class OnboardingStep {
    /** S-ONB-01 */
    Welcome,

    /** S-ONB-02 */
    StoryPreview,

    /** S-ONB-03 */
    Privacy,

    /** S-ONB-04 */
    AgeTerms,

    /** S-ONB-05 */
    CapturePromise,

    /** S-ONB-06 */
    Location,

    /** S-ONB-07 */
    Motion,

    /** S-ONB-08 */
    AutoTrips,

    /** S-ONB-09 */
    AllTheTime,

    /** S-ONB-10 */
    Notifications,

    /** S-ONB-12 */
    Battery,

    /** S-ONB-13 */
    HelpImprove,

    /** S-ONB-15 */
    Ready,
    ;

    /** The intro pages, which show Skip. Skip goes to [AgeTerms], which cannot be skipped (F-ONB-01.3). */
    val isIntro: Boolean get() = this == Welcome || this == StoryPreview || this == Privacy
}

/** Sheets over a page: S-ONB-05 "How it works", S-ONB-11 precise location, S-ONB-16 denied and manual-mode. */
enum class OnboardingSheet {
    HowItWorks,
    PreciseLocation,

    /** S-ONB-16 default: location was refused, so nothing records. */
    LocationDenied,

    /** S-ONB-16 alwaysDeclined: location is "while using the app", so automatic trips stay off. */
    ManualMode,
}

/** What the user has granted. Read from the system, never remembered, because it can change in Settings at any time. */
data class PermissionState(
    val fineLocation: Boolean = false,
    /** True when only approximate location was granted (S-ONB-11 asks for precise). */
    val approximateOnly: Boolean = false,
    val backgroundLocation: Boolean = false,
    val activityRecognition: Boolean = false,
    val notifications: Boolean = false,
) {
    /** Some location is granted, precise or approximate. */
    val hasLocation: Boolean get() = fineLocation || approximateOnly
}

/** What decides which pages are shown, besides the permissions. */
data class FlowContext(
    val permissions: PermissionState,
    /** The user switched "Record drives automatically" on in S-ONB-08 and has not backed out since. */
    val autoTripsOn: Boolean = false,
    /** The brand guide for S-ONB-12, or null on a Pixel or any brand without one. */
    val oemGuide: OemGuide? = null,
    val sdkInt: Int = Build.VERSION.SDK_INT,
)

/** The page order and which pages are skipped. Pure, so the ordering rules are unit-tested. */
object OnboardingFlow {
    private val order = OnboardingStep.entries

    /**
     * The page after [step], skipping pages that cannot or need not be shown (see [skips]). Returns null after the
     * last page.
     */
    fun next(step: OnboardingStep, context: FlowContext): OnboardingStep? =
        order.drop(order.indexOf(step) + 1).firstOrNull { !skips(it, context) }

    fun previous(step: OnboardingStep, context: FlowContext): OnboardingStep? =
        order.take(order.indexOf(step)).lastOrNull { !skips(it, context) }

    /** The pages the user will see, for the progress text. */
    fun shown(context: FlowContext): List<OnboardingStep> = order.filter { !skips(it, context) }

    /** "Setup, step n of N" for the numbered setup pages (age and terms to the analytics choice); null on the intro and Ready. */
    fun setupPosition(step: OnboardingStep, context: FlowContext): SetupPosition? {
        val setup = shown(context).filter { it.ordinal in SETUP_RANGE }
        val index = setup.indexOf(step)
        return if (index < 0) null else SetupPosition(index + 1, setup.size)
    }

    /**
     * - Without location nothing else can work, and the system will not offer background location: motion, automatic
     *   trips and the pages behind them are skipped, and the flow goes on to notifications (S-ONB-16 "Not now").
     * - Automatic trips (and so the all-the-time step and the battery step) need **precise** location and the user's
     *   opt-in; with only approximate location they are skipped and trips are "route only" (F-ONB-04.5).
     * - Android 10 has no separate settings step: its runtime dialog offers background location (S-ONB-09).
     * - A permission that is already granted is not asked for again; notifications need no runtime permission below
     *   Android 13.
     */
    @Suppress("CyclomaticComplexMethod") // one rule per page, kept together so the whole order reads in one place
    fun skips(step: OnboardingStep, c: FlowContext): Boolean {
        val p = c.permissions
        return when (step) {
            OnboardingStep.Location -> p.fineLocation
            OnboardingStep.Motion -> !p.hasLocation || p.activityRecognition
            OnboardingStep.AutoTrips -> !p.fineLocation
            OnboardingStep.AllTheTime ->
                !p.fineLocation || !c.autoTripsOn || p.backgroundLocation || c.sdkInt < Build.VERSION_CODES.R
            OnboardingStep.Notifications -> p.notifications || c.sdkInt < Build.VERSION_CODES.TIRAMISU
            OnboardingStep.Battery -> !c.autoTripsOn || c.oemGuide == null || !p.fineLocation
            else -> false
        }
    }

    private val SETUP_RANGE = OnboardingStep.AgeTerms.ordinal..OnboardingStep.HelpImprove.ordinal
}

/** "Setup, step [number] of [total]". */
data class SetupPosition(val number: Int, val total: Int)
