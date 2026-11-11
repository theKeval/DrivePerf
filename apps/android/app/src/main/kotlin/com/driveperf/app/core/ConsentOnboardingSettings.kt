package com.driveperf.app.core

import com.driveperf.feature.onboarding.OnboardingSettings
import com.driveperf.feature.onboarding.ReliabilityGuideState
import com.driveperf.platform.crash.ConsentSettings

/** What onboarding saves, on the app's private preferences (the feature module has no storage of its own). */
class ConsentOnboardingSettings(
    private val consent: ConsentSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) : OnboardingSettings {
    override var under18Blocked: Boolean
        get() = consent.under18Blocked
        set(value) {
            consent.under18Blocked = value
        }

    override var automaticTripsEnabled: Boolean
        get() = consent.automaticTripsEnabled
        set(value) {
            consent.automaticTripsEnabled = value
        }

    override fun recordTermsAccepted(version: String) {
        consent.termsAcceptedVersion = version
        consent.termsAcceptedAtMillis = clock()
    }

    override fun recordReliabilityGuide(state: ReliabilityGuideState) {
        consent.reliabilityGuide = when (state) {
            ReliabilityGuideState.Done -> ConsentSettings.RELIABILITY_DONE
            ReliabilityGuideState.RemindLater -> ConsentSettings.RELIABILITY_REMIND_LATER
        }
    }
}
