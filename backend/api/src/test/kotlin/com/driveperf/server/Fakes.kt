package com.driveperf.server

import com.driveperf.server.ports.AccountRecord
import com.driveperf.server.ports.AccountStatus
import com.driveperf.server.ports.AccountStore
import com.driveperf.server.ports.BlobSigner
import com.driveperf.server.ports.Clock
import com.driveperf.server.ports.DeletionRequest
import com.driveperf.server.ports.DeletionRequestStore
import com.driveperf.server.ports.DeletionStep
import com.driveperf.server.ports.IdentityAdmin
import com.driveperf.server.ports.ObjectInfo
import com.driveperf.server.ports.ObjectStore
import com.driveperf.server.ports.PendingUpload
import com.driveperf.server.ports.PresignedRequest
import com.driveperf.server.ports.RideAlongPasses
import com.driveperf.server.ports.SubscriberDirectory
import com.driveperf.server.ports.UploadLedger
import com.driveperf.server.ports.UserDataStore

/** In-memory stand-ins for the ports. Every call is recorded in [Journal] so tests can assert the order of effects. */
internal class Journal {
    val events = mutableListOf<String>()
}

internal class FakeClock(var now: Long = 1_762_000_000_000L) : Clock {
    override fun nowEpochMillis(): Long = now
}

internal class FakeObjectStore(
    private val journal: Journal,
    private val clock: FakeClock? = null,
    private val tickPerCall: Long = 0,
) : ObjectStore {
    val objects = linkedMapOf<String, ObjectInfo>()

    override fun head(key: String): ObjectInfo? = objects[key]

    override fun listKeys(prefix: String, limit: Int): List<String> = objects.keys.filter { it.startsWith(prefix) }.take(limit)

    override fun deleteKeys(keys: List<String>) {
        clock?.let { it.now += tickPerCall }
        journal.events += "objects.delete(${keys.size})"
        keys.forEach { objects.remove(it) }
    }
}

internal class FakeUserData(private val journal: Journal) : UserDataStore {
    val documents = mutableMapOf<String, Int>()
    var leaveBehindOnce: String? = null

    override fun deleteSome(accountId: String, maxDocuments: Int): Int {
        val have = documents[accountId] ?: 0
        val n = minOf(have, maxDocuments)
        documents[accountId] = have - n
        if (n > 0) journal.events += "userdata.delete($accountId,$n)"
        return n
    }

    override fun hasAnyData(accountId: String): Boolean = (documents[accountId] ?: 0) > 0
}

internal class FakeAccounts(private val journal: Journal) : AccountStore {
    val records = mutableMapOf<String, AccountRecord>()

    override fun find(accountId: String): AccountRecord? = records[accountId]

    override fun markDeleting(accountId: String, nowEpochMillis: Long) {
        journal.events += "account.markDeleting($accountId)"
        val old = records[accountId]
        records[accountId] = (old ?: AccountRecord(accountId, AccountStatus.ACTIVE, com.driveperf.server.ports.BackupState.OFF, null))
            .copy(status = AccountStatus.DELETING)
    }

    override fun delete(accountId: String) {
        journal.events += "account.delete($accountId)"
        records.remove(accountId)
    }
}

internal class FakeRequests(private val journal: Journal) : DeletionRequestStore {
    val requests = linkedMapOf<String, DeletionRequest>()
    val completed = mutableSetOf<String>()
    private val leased = mutableSetOf<String>()

    override fun createOrGet(accountId: String, nowEpochMillis: Long): DeletionRequest =
        requests.values.firstOrNull { it.accountId == accountId && it.id !in completed }
            ?: DeletionRequest("req-${requests.size + 1}", accountId, nowEpochMillis, emptySet()).also { requests[it.id] = it }

    override fun claimDue(nowEpochMillis: Long, leaseUntilEpochMillis: Long, limit: Int): List<DeletionRequest> =
        requests.values.filter { it.id !in completed && it.id !in leased }.take(limit).onEach { leased += it.id }

    override fun recordStep(requestId: String, step: DeletionStep) {
        journal.events += "step($step)"
        requests[requestId] = requests.getValue(requestId).let { it.copy(completedSteps = it.completedSteps + step) }
    }

    override fun resetSteps(requestId: String) {
        journal.events += "steps.reset"
        requests[requestId] = requests.getValue(requestId).copy(completedSteps = emptySet())
    }

    override fun complete(requestId: String, nowEpochMillis: Long) {
        journal.events += "request.complete"
        completed += requestId
    }

    fun release() = leased.clear()
}

internal class FakeExternal(private val journal: Journal) : SubscriberDirectory, RideAlongPasses, IdentityAdmin {
    override fun deleteSubscriber(accountId: String) { journal.events += "revenuecat.delete($accountId)" }

    override fun forgetAccount(accountId: String) { journal.events += "ridealong.forget($accountId)" }

    override fun revokeRefreshTokens(accountId: String) { journal.events += "auth.revoke($accountId)" }

    override fun deleteUser(accountId: String) { journal.events += "auth.delete($accountId)" }
}

internal class FakeLedger : UploadLedger {
    private val rows = mutableMapOf<Pair<String, String>, PendingUpload>()

    override fun record(accountId: String, upload: PendingUpload) { rows[accountId to upload.key] = upload }

    override fun find(accountId: String, key: String): PendingUpload? = rows[accountId to key]

    override fun latestExpiryEpochMillis(accountId: String): Long? =
        rows.filterKeys { it.first == accountId }.values.maxOfOrNull { it.expiresAtEpochMillis }
}

internal class FakeSigner : BlobSigner {
    var lastPut: Triple<String, Map<String, String>, Int>? = null

    override fun presignPut(objectKey: String, headers: Map<String, String>, ttlSeconds: Int): PresignedRequest {
        lastPut = Triple(objectKey, headers, ttlSeconds)
        return PresignedRequest("https://store.test/$objectKey?sig=1", "PUT", headers, 1_000L + ttlSeconds * 1000L)
    }

    override fun presignGet(objectKey: String, ttlSeconds: Int): PresignedRequest =
        PresignedRequest("https://store.test/$objectKey?sig=2", "GET", emptyMap(), 0)
}
