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
import com.driveperf.server.ports.UserDataStore

/** Every place that can hold data about one account, besides the account record itself. */
internal class AccountDataSources(
    val objects: ObjectStore,
    val userData: UserDataStore,
    val subscribers: SubscriberDirectory,
    val rideAlong: RideAlongPasses,
    val identity: IdentityAdmin,
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

    enum class Outcome { DONE, MORE_WORK }

    private enum class StepResult { DONE, OUT_OF_TIME, LEFTOVERS }

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
                StepResult.OUT_OF_TIME -> outcome = Outcome.MORE_WORK
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
        DeletionStep.VERIFIED -> if (hasLeftovers(accountId)) StepResult.LEFTOVERS else StepResult.DONE
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

    private fun hasLeftovers(accountId: String): Boolean =
        userData.hasAnyData(accountId) || AccountIds.objectPrefixes(accountId).any { objects.listKeys(it, 1).isNotEmpty() }

    private companion object {
        const val OBJECT_BATCH = 1000
        const val DOCUMENT_BATCH = 500
    }
}
