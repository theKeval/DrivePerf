package com.driveperf.domain.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class TestEvent(override val name: String, override val properties: Map<String, String> = emptyMap()) :
    AnalyticsEvent

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

class ConsentGatedAnalyticsTest {
    private val sink = RecordingSink()
    private val analytics = ConsentGatedAnalytics(sink, bufferLimit = 3)

    @Test
    fun nothingReachesTheSinkBeforeTheChoice() {
        analytics.track(TestEvent("onboarding_started"))
        analytics.track(TestEvent("onboarding_step_viewed"))

        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun grantingSendsTheHeldEventsInOrder() {
        analytics.track(TestEvent("a"))
        analytics.track(TestEvent("b"))

        analytics.setUsageConsent(true)

        assertEquals(listOf("enabled=true", "log:a", "log:b"), sink.calls)
    }

    @Test
    fun decliningDropsTheHeldEventsAndLaterOnes() {
        analytics.track(TestEvent("a"))

        analytics.setUsageConsent(false)
        analytics.track(TestEvent("b"))

        assertEquals(listOf("enabled=false"), sink.calls)
    }

    @Test
    fun declinedEventsAreNotSentEvenIfConsentIsGrantedLater() {
        analytics.track(TestEvent("held"))
        analytics.setUsageConsent(false)
        analytics.track(TestEvent("dropped"))

        analytics.setUsageConsent(true)
        analytics.track(TestEvent("after"))

        assertEquals(listOf("enabled=false", "enabled=true", "log:after"), sink.calls)
    }

    @Test
    fun afterGrantingEventsGoStraightThrough() {
        analytics.setUsageConsent(true)
        analytics.track(TestEvent("a"))

        assertEquals(listOf("enabled=true", "log:a"), sink.calls)
    }

    @Test
    fun turningOffMidSessionStopsEventsAtOnce() {
        analytics.setUsageConsent(true)
        analytics.track(TestEvent("a"))

        analytics.setUsageConsent(false)
        analytics.track(TestEvent("b"))

        assertEquals(listOf("enabled=true", "log:a", "enabled=false"), sink.calls)
    }

    @Test
    fun theBufferKeepsTheNewestEvents() {
        listOf("a", "b", "c", "d").forEach { analytics.track(TestEvent(it)) }

        analytics.setUsageConsent(true)

        assertEquals(listOf("enabled=true", "log:b", "log:c", "log:d"), sink.calls)
    }

    @Test
    fun resetIdPassesThrough() {
        analytics.resetId()

        assertEquals(listOf("reset"), sink.calls)
    }

    @Test
    fun theNoOpSinkAcceptsEverything() {
        val gated = ConsentGatedAnalytics(NoOpAnalyticsSink)

        gated.setUsageConsent(true)
        gated.track(TestEvent("a"))
        gated.resetId()
    }
}
