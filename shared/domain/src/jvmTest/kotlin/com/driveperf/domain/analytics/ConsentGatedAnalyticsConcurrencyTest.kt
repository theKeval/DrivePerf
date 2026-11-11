package com.driveperf.domain.analytics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class Event(override val name: String) : AnalyticsEvent {
    override val properties: Map<String, String> = emptyMap()
}

/** Codex finding on DRIVE-70: consent changes and sink calls must not interleave across threads. */
class ConsentGatedAnalyticsConcurrencyTest {
    private class OrderedSink : AnalyticsSink {
        private val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val snapshot: List<String> get() = synchronized(calls) { calls.toList() }

        override fun setCollectionEnabled(enabled: Boolean) {
            calls += "enabled=$enabled"
        }

        override fun log(event: AnalyticsEvent) {
            calls += "log"
        }

        override fun resetId() {
            calls += "reset"
        }
    }

    @Test
    fun noEventIsLoggedWhileCollectionIsDisabled() {
        repeat(50) {
            val sink = OrderedSink()
            val gate = ConsentGatedAnalytics(sink, bufferLimit = 1_000)
            val pool = Executors.newFixedThreadPool(8)
            val start = CountDownLatch(1)
            repeat(6) { t ->
                pool.execute {
                    start.await()
                    repeat(200) { gate.track(Event("e$t")) }
                }
            }
            pool.execute {
                start.await()
                repeat(40) {
                    gate.setUsageConsent(true)
                    gate.setUsageConsent(false)
                }
                gate.setUsageConsent(false)
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

            // Replay the sink's calls: a log is only valid while collection is on.
            var enabled = false
            sink.snapshot.forEach { call ->
                when {
                    call == "enabled=true" -> enabled = true
                    call == "enabled=false" -> enabled = false
                    call == "log" -> assertTrue(enabled, "an event reached the sink while collection was off")
                }
            }
            assertEquals("enabled=false", sink.snapshot.last { it.startsWith("enabled") }, "the last consent wins")
        }
    }
}
