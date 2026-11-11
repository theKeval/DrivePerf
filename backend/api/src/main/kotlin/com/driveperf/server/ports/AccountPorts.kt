package com.driveperf.server.ports

internal enum class AccountStatus { ACTIVE, DELETING }

/** Backup state machine of data-model.md 5.3. */
internal enum class BackupState { OFF, ACTIVE, LAPSED, PURGING }

/** The server-owned `accounts/{accountId}` record, reduced to what the use cases read. */
internal data class AccountRecord(
    val accountId: String,
    val status: AccountStatus,
    val backupState: BackupState,
    val writeUntilEpochMillis: Long?,
)

internal interface AccountStore {
    fun find(accountId: String): AccountRecord?

    /** From now on the API refuses every call for the account. Creates the record for a Free account that has none. */
    fun markDeleting(accountId: String, nowEpochMillis: Long)

    /** Removes the record. Idempotent. */
    fun delete(accountId: String)
}

/** The steps of the deletion cascade (data-model.md 5.5), in the order they run. */
internal enum class DeletionStep { OBJECTS, USER_DATA, SUBSCRIBER, RIDE_ALONG, VERIFIED, ACCOUNT }

internal data class DeletionRequest(
    val id: String,
    val accountId: String,
    val requestedAtEpochMillis: Long,
    val completedSteps: Set<DeletionStep>,
)

internal interface DeletionRequestStore {
    /** One open request per account: asking twice returns the first. */
    fun createOrGet(accountId: String, nowEpochMillis: Long): DeletionRequest

    /** Open requests nobody holds a lease on; sets the lease on each one it returns. */
    fun claimDue(nowEpochMillis: Long, leaseUntilEpochMillis: Long, limit: Int): List<DeletionRequest>

    fun recordStep(requestId: String, step: DeletionStep)

    /** Back to the first step, because the verification found something left behind. */
    fun resetSteps(requestId: String)

    fun complete(requestId: String, nowEpochMillis: Long)
}

/** Everything under `users/{accountId}` (all subcollections, consents included). */
internal interface UserDataStore {
    /** Deletes up to [maxDocuments] documents and returns how many it deleted; 0 means nothing is left. */
    fun deleteSome(accountId: String, maxDocuments: Int): Int

    fun hasAnyData(accountId: String): Boolean
}

/** RevenueCat: removes the subscriber and the purchase-identity link. */
internal interface SubscriberDirectory {
    fun deleteSubscriber(accountId: String)
}

/** Ride-along passes (F-SHR-05): passes the user sent are deleted, their ID in other people's redemptions becomes `deleted`. */
internal interface RideAlongPasses {
    fun forgetAccount(accountId: String)
}

/** The identity provider (Firebase Auth today). Both calls are idempotent. */
internal interface IdentityAdmin {
    fun revokeRefreshTokens(accountId: String)

    fun deleteUser(accountId: String)
}
