package com.driveperf.platform.activity

import com.driveperf.platform.ports.ActivityEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-wide hand-over from the manifest-declared [ActivityTransitionReceiver] (which the OS creates, so it cannot be
 * given constructor arguments) to whoever collects [ActivityTransitionSource.events].
 */
internal object ActivityEventBus {
    private val mutableEvents = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ActivityEvent> = mutableEvents

    fun publish(event: ActivityEvent) {
        mutableEvents.tryEmit(event)
    }
}
