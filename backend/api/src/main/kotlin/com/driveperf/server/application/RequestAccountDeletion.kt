package com.driveperf.server.application

import com.driveperf.server.ports.AccountStore
import com.driveperf.server.ports.Clock
import com.driveperf.server.ports.DeletionRequestStore
import com.driveperf.server.ports.IdentityAdmin

/**
 * `POST /v1/account:delete` (data-model.md 5.5, step 1). Synchronous part only: after this the API refuses every call
 * for the account, refresh tokens are revoked and the hourly job does the rest. Asking twice is safe.
 */
internal class RequestAccountDeletion(
    private val accounts: AccountStore,
    private val requests: DeletionRequestStore,
    private val identity: IdentityAdmin,
    private val clock: Clock,
) {
    sealed interface Result {
        data class Accepted(val requestId: String, val completeByEpochMillis: Long) : Result

        /** The ID token's `auth_time` is too old (`reauth_required`). */
        data object ReauthRequired : Result

        data object InvalidAccount : Result
    }

    operator fun invoke(accountId: String, authTimeEpochMillis: Long): Result {
        val now = clock.nowEpochMillis()
        return when {
            !AccountIds.isValid(accountId) -> Result.InvalidAccount
            now - authTimeEpochMillis > RECENT_SIGN_IN_MILLIS -> Result.ReauthRequired
            else -> {
                accounts.markDeleting(accountId, now)
                val request = requests.createOrGet(accountId, now)
                identity.revokeRefreshTokens(accountId)
                Result.Accepted(request.id, request.requestedAtEpochMillis + SLA_MILLIS)
            }
        }
    }

    private companion object {
        const val RECENT_SIGN_IN_MILLIS = 5 * 60 * 1000L
        const val SLA_MILLIS = 24 * 60 * 60 * 1000L
    }
}
