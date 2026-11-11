package com.driveperf.platform.capture

import com.driveperf.foundation.DomainError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Where the adapters report a failure the shared ports have no channel for. `LocationSource.start` returns nothing, so a
 * missing permission or sensor surfaces here as a typed [DomainError.Capture] (ADR-002 section 7) instead of an
 * exception. Whoever runs the trip (today the foreground service) decides what a failure means for it.
 */
class CaptureFailureBus {
    private val mutableFailures = MutableSharedFlow<DomainError.Capture>(
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val failures: SharedFlow<DomainError.Capture> = mutableFailures

    fun report(failure: DomainError.Capture) {
        mutableFailures.tryEmit(failure)
    }

    private companion object {
        const val BUFFER = 8
    }
}
