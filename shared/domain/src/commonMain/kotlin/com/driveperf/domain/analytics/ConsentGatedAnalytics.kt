package com.driveperf.domain.analytics

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [Analytics] behind the user's usage-statistics choice (F-ONB-08, ADR-007).
 *
 * - Until [setUsageConsent] is first called the choice is **undecided**: events wait in a bounded in-memory buffer.
 *   Nothing is written to disk and the sink is not touched.
 * - `setUsageConsent(true)` enables collection and sends the buffer in order. `setUsageConsent(false)` disables
 *   collection and drops the buffer; later events are dropped.
 * - A later change replaces the state at once, so it takes effect within the session.
 *
 * Thread-safe: every state change and the sink calls that go with it run under one lock, so the last consent wins,
 * an event is never sent before collection is enabled or ahead of the buffered ones, and a revocation can't be undone
 * by an older grant. The lock is held only for in-memory work and the sink call, which is fast and never calls back.
 *
 * @param bufferLimit the most events held while undecided; the oldest are dropped first.
 */
public class ConsentGatedAnalytics(
    private val sink: AnalyticsSink,
    private val bufferLimit: Int = DEFAULT_BUFFER_LIMIT,
) : Analytics {
    // Plain state, guarded by `lock`. A spin lock built on a state flow's compare-and-set works in common code on every
    // target; critical sections are tiny, so waiting is negligible.
    private val lock = MutableStateFlow(false)
    private var allowed: Boolean? = null
    private var held: List<AnalyticsEvent> = emptyList()

    override fun track(event: AnalyticsEvent) {
        locked {
            when (allowed) {
                true -> sink.log(event)
                false -> Unit
                null -> held = (held + event).takeLast(bufferLimit)
            }
        }
    }

    override fun setUsageConsent(allowed: Boolean) {
        locked {
            val previouslyHeld = held
            this.allowed = allowed
            held = emptyList()
            sink.setCollectionEnabled(allowed)
            if (allowed) previouslyHeld.forEach(sink::log)
        }
    }

    override fun resetId() {
        locked { sink.resetId() }
    }

    private inline fun locked(body: () -> Unit) {
        while (!lock.compareAndSet(expect = false, update = true)) {
            // Another thread is inside; its work is short.
        }
        try {
            body()
        } finally {
            lock.value = false
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_LIMIT = 100
    }
}
