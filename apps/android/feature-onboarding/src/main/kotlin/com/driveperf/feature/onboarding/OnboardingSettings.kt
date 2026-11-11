package com.driveperf.feature.onboarding

/** How the user answered the battery step (S-ONB-12), kept so the Health Check can remind them (F-SET-03). */
enum class ReliabilityGuideState { Done, RemindLater }

/**
 * What onboarding saves on this phone. The feature module has no storage of its own (ADR-002): the app implements this
 * on its preferences. Nothing here leaves the phone.
 */
interface OnboardingSettings {
    /** F-ONB-02.2: only a local flag is stored when the user says they are under 18. */
    var under18Blocked: Boolean

    /** F-ONB-02.3: the accepted version of the Terms and Privacy Policy. The implementation stamps the date. */
    fun recordTermsAccepted(version: String)

    /** S-ONB-08: automatic trips are off until the user turns them on. */
    var automaticTripsEnabled: Boolean

    fun recordReliabilityGuide(state: ReliabilityGuideState)
}

/** The version of the Terms of Use and Privacy Policy on driveperf.com that the age step saves (same value as iOS). */
const val TERMS_VERSION = "2026-11"

/** S-WEB-03, S-WEB-04 and S-WEB-06: opened in a Custom Tab, inside the app (F-ONB-02.3, F-ONB-03.3). */
object OnboardingLinks {
    const val TERMS = "https://driveperf.com/terms"
    const val PRIVACY = "https://driveperf.com/privacy"
    const val HOW_IT_RECORDS = "https://driveperf.com/how-it-records"
}
