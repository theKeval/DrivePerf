package com.driveperf.platform.crash

import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.analytics.AnalyticsSink
import com.driveperf.domain.analytics.ConsentGatedAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyChoicesTest {
    private class FakeSettings : ConsentSettings {
        override var crashReportsAllowed = false
        override var usageStatisticsAllowed = false
        override var usageStatisticsDecided = false
        override var onboardingCompleted = false
        override var termsAcceptedVersion: String? = null
        override var termsAcceptedAtMillis = 0L
        override var under18Blocked = false
        override var automaticTripsEnabled = false
        override var reliabilityGuide: String? = null
    }

    private class FakeCrashReporter : CrashReporter {
        override val isAvailable = true
        var on = false

        override fun setEnabled(enabled: Boolean) {
            on = enabled
        }
    }

    private class RecordingSink : AnalyticsSink {
        val calls = mutableListOf<String>()

        override fun setCollectionEnabled(enabled: Boolean) {
            calls += "enabled=$enabled"
        }

        override fun log(event: AnalyticsEvent) {
            calls += "log:${event.name}"
        }

        override fun resetId() {
            calls += "reset"
        }
    }

    private class Event(override val name: String) : AnalyticsEvent {
        override val properties: Map<String, String> = emptyMap()
    }

    private val settings = FakeSettings()
    private val crash = FakeCrashReporter()
    private val sink = RecordingSink()
    private val analytics = ConsentGatedAnalytics(sink)
    private val choices = PrivacyChoices(settings, crash, analytics)

    @Test
    fun beforeTheUserChoosesNothingIsSwitchedOnAndEventsAreHeld() {
        choices.restore()
        analytics.track(Event("onboarding_started"))

        assertFalse(crash.on)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun aStoredChoiceIsAppliedAtLaunch() {
        settings.crashReportsAllowed = true
        settings.usageStatisticsDecided = true
        settings.usageStatisticsAllowed = true

        choices.restore()
        analytics.track(Event("a"))

        assertTrue(crash.on)
        assertEquals(listOf("enabled=true", "log:a"), sink.calls)
    }

    @Test
    fun aStoredDeclineKeepsCollectionOff() {
        settings.usageStatisticsDecided = true

        choices.restore()
        analytics.track(Event("a"))

        assertEquals(listOf("enabled=false"), sink.calls)
    }

    @Test
    fun grantingUsageSendsWhatWasHeldAndRemembersTheChoice() {
        analytics.track(Event("held"))

        choices.setUsageStatistics(true)

        assertEquals(listOf("enabled=true", "log:held"), sink.calls)
        assertTrue(settings.usageStatisticsAllowed)
        assertTrue(settings.usageStatisticsDecided)
    }

    @Test
    fun decliningUsageDropsWhatWasHeld() {
        analytics.track(Event("held"))

        choices.setUsageStatistics(false)
        analytics.track(Event("later"))

        assertEquals(listOf("enabled=false"), sink.calls)
        assertTrue(settings.usageStatisticsDecided)
    }

    @Test
    fun turningUsageOffLaterStopsEventsInTheSameSession() {
        choices.setUsageStatistics(true)
        choices.setUsageStatistics(false)
        analytics.track(Event("a"))

        assertEquals(listOf("enabled=true", "enabled=false"), sink.calls)
    }

    @Test
    fun crashReportsAreSeparateFromUsage() {
        choices.setCrashReports(true)

        assertTrue(crash.on)
        assertFalse(settings.usageStatisticsDecided)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun resetIdReachesTheSink() {
        choices.resetAnalyticsId()

        assertEquals(listOf("reset"), sink.calls)
    }
}
