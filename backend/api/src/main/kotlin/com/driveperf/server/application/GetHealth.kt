package com.driveperf.server.application

import com.driveperf.server.ports.Clock

/** Liveness check use case. Real use cases (SyncChanges, IssueUploadTicket, ...) arrive with DRIVE-76. */
internal class GetHealth(private val clock: Clock) {
    data class Health(val status: String)

    operator fun invoke(): Health = Health(status = if (clock.nowEpochMillis() > 0) "ok" else "clock-unset")
}
