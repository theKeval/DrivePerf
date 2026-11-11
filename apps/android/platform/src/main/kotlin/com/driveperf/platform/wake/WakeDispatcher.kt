package com.driveperf.platform.wake

import com.driveperf.domain.capture.WakeEvent
import java.util.concurrent.atomic.AtomicReference

/**
 * Hand-over from the manifest-declared wake receivers (created by the OS, so they take no constructor arguments) to
 * the app, which starts capture for the wake. `Application.onCreate` always runs before a receiver, so the app sets
 * the handler there; a wake that arrives without one is dropped.
 */
object WakeDispatcher {
    private val handler = AtomicReference<((WakeEvent) -> Unit)?>(null)

    fun setHandler(onWake: ((WakeEvent) -> Unit)?) = handler.set(onWake)

    fun dispatch(event: WakeEvent) {
        handler.get()?.invoke(event)
    }
}
