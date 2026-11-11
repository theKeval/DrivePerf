package com.driveperf.server.application

import com.driveperf.server.ports.AccountStore
import com.driveperf.server.ports.Clock
import com.driveperf.server.ports.DeletionRequest
import com.driveperf.server.ports.DeletionRequestStore
import com.driveperf.server.ports.DeletionStep
import com.driveperf.server.ports.IdentityAdmin
import com.driveperf.server.ports.ObjectStore
import com.driveperf.server.ports.RideAlongPasses
import com.driveperf.server.ports.SubscriberDirectory
import com.driveperf.server.ports.UploadLedger
import com.driveperf.server.ports.UserDataStore

/** Every place that can hold data about one account, besides the account record itself. */
internal class AccountDataSources(
    val objects: ObjectStore,
    val userData: UserDataStore,
    val subscribers: SubscriberDirectory,
    val rideAlong: RideAlongPasses,
    val identity: IdentityAdmin,
    val uploads: UploadLedger,
)

/**
 * The account-deletion cascade (F-PRV-05, data-model.md 5.5 steps 2 to 7). Each step is idempotent and its completion
 * is recorded, so a run that ends early (time box, crash) resumes where it stopped. Order matters: trip files and
 * records go first, the identity and the account record last, and only after a verification sweep found nothing left.
 */
internal class DeleteAccountCascade(
    private val sources: AccountDataSources,
    private val accounts: AccountStore,
    private val requests: DeletionRequestStore,
    private val clock: Clock,
) {
    private val objects get() = sources.objects
    private val userData get() = sources.userData
    private val subscribers get() = sources.subscribers
    private val rideAlong get() = sources.rideAlong
    private val identity get() = sources.identity
    private val uploads get() = sources.uploads

    enum class Outcome { DONE, MORE_WORK }

    private enum class StepResult { DONE, OUT_OF_TIME, LEFTOVERS, WAITING }

    fun run(request: DeletionRequest, deadlineEpochMillis: Long): Outcome {
        check(AccountIds.isValid(request.accountId)) { "invalid account id in deletion request ${request.id}" }
        var outcome = Outcome.DONE
        for (step in DeletionStep.entries.filter { it !in request.completedSteps }) {
            val result = if (clock.nowEpochMillis() >= deadlineEpochMillis) {
                StepResult.OUT_OF_TIME
            } else {
                execute(step, request.accountId, deadlineEpochMillis)
            }
            when (result) {
                StepResult.DONE -> requests.recordStep(request.id, step)
                StepResult.OUT_OF_TIME, StepResult.WAITING -> outcome = Outcome.MORE_WORK
                StepResult.LEFTOVERS -> {
                    requests.resetSteps(request.id)
                    outcome = Outcome.MORE_WORK
                }
            }
            if (outcome == Outcome.MORE_WORK) break
        }
        if (outcome == Outcome.DONE) requests.complete(request.id, clock.nowEpochMillis())
        return outcome
    }

    private fun execute(step: DeletionStep, accountId: String, deadline: Long): StepResult = when (step) {
        DeletionStep.OBJECTS -> deleteObjects(accountId, deadline)
        DeletionStep.USER_DATA -> deleteUserData(accountId, deadline)
        DeletionStep.SUBSCRIBER -> done { subscribers.deleteSubscriber(accountId) }
        DeletionStep.RIDE_ALONG -> done { rideAlong.forgetAccount(accountId) }
        DeletionStep.VERIFIED -> verify(accountId)
        DeletionStep.ACCOUNT -> done {
            identity.deleteUser(accountId)
            accounts.delete(accountId)
        }
    }

    private fun done(action: () -> Unit): StepResult {
        action()
        return StepResult.DONE
    }

    private fun deleteObjects(accountId: String, deadline: Long): StepResult {
        var outOfTime = false
        for (prefix in AccountIds.objectPrefixes(accountId)) {
            var keys = objects.listKeys(prefix, OBJECT_BATCH)
            while (keys.isNotEmpty() && !outOfTime) {
                objects.deleteKeys(keys)
                outOfTime = clock.nowEpochMillis() >= deadline
                if (!outOfTime) keys = objects.listKeys(prefix, OBJECT_BATCH)
            }
        }
        return if (outOfTime) StepResult.OUT_OF_TIME else StepResult.DONE
    }

    private fun deleteUserData(accountId: String, deadline: Long): StepResult {
        var deleted = userData.deleteSome(accountId, DOCUMENT_BATCH)
        while (deleted > 0 && clock.nowEpochMillis() < deadline) deleted = userData.deleteSome(accountId, DOCUMENT_BATCH)
        return if (deleted > 0) StepResult.OUT_OF_TIME else StepResult.DONE
    }

    /**
     * A presigned PUT cannot be revoked and the account is only marked as deleting, so an upload started just before the
     * request can still land after the objects were listed. The sweep therefore waits until every URL issued for the
     * account has expired, plus a grace period for a transfer that began just before; a late object is then found by
     * the sweep and the cascade starts over.
     */
    private fun verify(accountId: String): StepResult {
        val lastUsable = uploads.latestExpiryEpochMillis(accountId)?.plus(IN_FLIGHT_GRACE_MILLIS)
        return when {
            lastUsable != null && clock.nowEpochMillis() < lastUsable -> StepResult.WAITING
            hasLeftovers(accountId) -> StepResult.LEFTOVERS
            else -> StepResult.DONE
        }
    }

    private fun hasLeftovers(accountId: String): Boolean =
        userData.hasAnyData(accountId) || AccountIds.objectPrefixes(accountId).any { objects.listKeys(it, 1).isNotEmpty() }

    private companion object {
        const val OBJECT_BATCH = 1000
        const val DOCUMENT_BATCH = 500
        const val IN_FLIGHT_GRACE_MILLIS = 5 * 60 * 1000L
    }
}
