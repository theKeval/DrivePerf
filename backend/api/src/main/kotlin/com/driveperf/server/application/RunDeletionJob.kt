package com.driveperf.server.application

import com.driveperf.server.ports.Clock
import com.driveperf.server.ports.DeletionRequestStore

/**
 * `POST /v1/jobs/deletion`, called hourly by Cloud Scheduler (data-model.md 5.4). Works for a time-boxed batch
 * (the API's request timeout is 60 s) and says whether work remains, so the scheduler's retry carries on.
 */
internal class RunDeletionJob(
    private val requests: DeletionRequestStore,
    private val cascade: DeleteAccountCascade,
    private val clock: Clock,
    private val budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
) {
    data class Result(val more: Boolean, val processed: Map<String, Int>)

    operator fun invoke(): Result {
        val started = clock.nowEpochMillis()
        val deadline = started + budgetMillis
        val claimed = requests.claimDue(started, leaseUntilEpochMillis = deadline + LEASE_SLACK_MILLIS, limit = BATCH)
        var completed = 0
        var unfinished = 0
        for (request in claimed) {
            when (cascade.run(request, deadline)) {
                DeleteAccountCascade.Outcome.DONE -> completed++
                DeleteAccountCascade.Outcome.MORE_WORK -> unfinished++
            }
        }
        return Result(
            more = unfinished > 0 || claimed.size == BATCH,
            processed = mapOf("claimed" to claimed.size, "completed" to completed, "unfinished" to unfinished),
        )
    }

    private companion object {
        const val DEFAULT_BUDGET_MILLIS = 45_000L
        const val LEASE_SLACK_MILLIS = 15 * 60 * 1000L
        const val BATCH = 20
    }
}
