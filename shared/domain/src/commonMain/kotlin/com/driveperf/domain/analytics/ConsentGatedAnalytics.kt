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
 * Thread-safe without locks: the state is replaced by compare-and-set, and the sink is called only after the state
 * that allows the call has been published.
 *
 * @param bufferLimit the most events held while undecided; the oldest are dropped first.
 */
public class ConsentGatedAnalytics(
    private val sink: AnalyticsSink,
    private val bufferLimit: Int = DEFAULT_BUFFER_LIMIT,
) : Analytics {
    private class State(val allowed: Boolean?, val held: List<AnalyticsEvent>)

    private val state = MutableStateFlow(State(allowed = null, held = emptyList()))

    override fun track(event: AnalyticsEvent) {
        var current = state.value
        while (current.allowed == null) {
            if (state.compareAndSet(current, State(null, (current.held + event).takeLast(bufferLimit)))) return
            current = state.value
        }
        if (current.allowed == true) sink.log(event)
    }

    override fun setUsageConsent(allowed: Boolean) {
        val previous = state.getAndSet(State(allowed, emptyList()))
        sink.setCollectionEnabled(allowed)
        if (allowed) previous.held.forEach(sink::log)
    }

    override fun resetId() {
        sink.resetId()
    }

    private companion object {
        const val DEFAULT_BUFFER_LIMIT = 100
    }
}

private fun <T> MutableStateFlow<T>.getAndSet(next: T): T {
    while (true) {
        val current = value
        if (compareAndSet(current, next)) return current
    }
}
