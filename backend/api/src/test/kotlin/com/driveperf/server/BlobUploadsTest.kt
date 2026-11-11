package com.driveperf.server

import com.driveperf.server.application.BlobKey
import com.driveperf.server.application.BlobRejection
import com.driveperf.server.application.CommitBlobUpload
import com.driveperf.server.application.PrepareBlobRequest
import com.driveperf.server.application.PrepareBlobUpload
import com.driveperf.server.ports.AccountRecord
import com.driveperf.server.ports.AccountStatus
import com.driveperf.server.ports.BackupState
import com.driveperf.server.ports.ObjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlobUploadsTest {
    private val journal = Journal()
    private val clock = FakeClock()
    private val accounts = FakeAccounts(journal)
    private val objects = FakeObjectStore(journal)
    private val signer = FakeSigner()
    private val ledger = FakeLedger()
    private val prepare = PrepareBlobUpload(accounts, objects, signer, ledger, clock)
    private val commit = CommitBlobUpload(objects, ledger)

    private val sha = "9f2c41aa07be3d55a8a1e2c0b6d47f3c1e9b5a6d7c8e9f001122334455667788"
    private val trip = "0193f7a2-5b1e-7c3d-9a10-4f2e8b6c1d20"
    private fun key(account: String = "alice") = "u/$account/t/$trip/r1.v1.${sha.take(16)}.pb.gz"
    private fun request(key: String = key(), size: Long = 301_230, type: String = "application/x-protobuf", hash: String = sha) =
        PrepareBlobRequest(key, hash, size, type)

    private fun active(until: Long? = null, state: BackupState = BackupState.ACTIVE) {
        accounts.records["alice"] = AccountRecord("alice", AccountStatus.ACTIVE, state, until)
    }

    private fun rejected(result: PrepareBlobUpload.Result): BlobRejection =
        (result as PrepareBlobUpload.Result.Rejected).reason

    @Test
    fun keyParsingAcceptsTheDocumentedLayoutOnly() {
        val parsed = assertNotNull(BlobKey.parse(key()))
        assertEquals("alice", parsed.accountId)
        assertEquals(trip, parsed.tripId)
        assertEquals(1, parsed.formatVersion)
        for (bad in listOf("", "u/alice/t/$trip/r1.v1.${sha.take(16)}.pb", "u/../t/$trip/r1.v1.${sha.take(16)}.pb.gz",
            "u/al/ice/t/$trip/r1.v1.${sha.take(16)}.pb.gz", "x/alice/t/$trip/r1.v1.${sha.take(16)}.pb.gz",
            "u/alice/t/not-a-uuid/r1.v1.${sha.take(16)}.pb.gz", "u/alice/t/$trip/r1.v1.${sha.take(15)}.pb.gz",
            "u/alice/t/$trip/r1.v1.${sha.take(16).uppercase()}.pb.gz")) {
            assertNull(BlobKey.parse(bad), bad)
        }
    }

    @Test
    fun mintsAPresignedPutWithBoundHeadersForAnActiveBackup() {
        active()
        val result = prepare("alice", request()) as PrepareBlobUpload.Result.Upload

        assertEquals("PUT", result.request.method)
        val (objectKey, headers, ttl) = assertNotNull(signer.lastPut)
        assertEquals(key(), objectKey)
        assertEquals(900, ttl)
        assertEquals(
            mapOf(
                "Content-Type" to "application/x-protobuf",
                "Content-Length" to "301230",
                "x-amz-meta-sha256" to sha,
                "x-amz-meta-tripid" to trip,
                "x-amz-meta-formatversion" to "1",
            ),
            headers,
        )
        assertNotNull(ledger.find("alice", key()))
    }

    @Test
    fun answersExistsWhenTheSameObjectIsAlreadyStored() {
        active()
        objects.objects[key()] = ObjectInfo(301_230, mapOf("sha256" to sha))

        assertEquals(PrepareBlobUpload.Result.AlreadyStored, prepare("alice", request()))
        assertNull(signer.lastPut)
    }

    @Test
    fun uploadsAgainWhenTheStoredObjectDiffers() {
        active()
        objects.objects[key()] = ObjectInfo(10, mapOf("sha256" to sha))

        assertTrue(prepare("alice", request()) is PrepareBlobUpload.Result.Upload)
    }

    @Test
    fun refusesSomeoneElsesAccountSegment() {
        active()
        assertEquals(BlobRejection.FORBIDDEN, rejected(prepare("alice", request(key("mallory")))))
    }

    @Test
    fun refusesBackupThatIsNotActiveOrHasExpired() {
        for (state in listOf(BackupState.OFF, BackupState.LAPSED, BackupState.PURGING)) {
            active(state = state)
            assertEquals(BlobRejection.BACKUP_NOT_ALLOWED, rejected(prepare("alice", request())), state.name)
        }
        active(until = clock.now - 1)
        assertEquals(BlobRejection.BACKUP_NOT_ALLOWED, rejected(prepare("alice", request())))
        accounts.records.remove("alice")
        assertEquals(BlobRejection.BACKUP_NOT_ALLOWED, rejected(prepare("alice", request())))
    }

    @Test
    fun refusesAnAccountThatIsBeingDeleted() {
        accounts.records["alice"] = AccountRecord("alice", AccountStatus.DELETING, BackupState.ACTIVE, null)
        assertEquals(BlobRejection.ACCOUNT_DELETING, rejected(prepare("alice", request())))
    }

    @Test
    fun validatesHashSizeAndContentType() {
        active()
        assertEquals(BlobRejection.VALIDATION, rejected(prepare("alice", request(hash = "0".repeat(64)))))
        assertEquals(BlobRejection.VALIDATION, rejected(prepare("alice", request(hash = sha.take(63)))))
        assertEquals(BlobRejection.VALIDATION, rejected(prepare("alice", request(type = "text/plain"))))
        assertEquals(BlobRejection.VALIDATION, rejected(prepare("alice", request(size = 0))))
        assertEquals(BlobRejection.VALIDATION, rejected(prepare("alice", request(key = "u/alice/../x"))))
        assertEquals(BlobRejection.TOO_LARGE, rejected(prepare("alice", request(size = 16L * 1024 * 1024 + 1))))
        assertNull(signer.lastPut)
    }

    @Test
    fun commitChecksTheStoredObjectAgainstWhatWasAnnounced() {
        active()
        prepare("alice", request())

        assertEquals(BlobRejection.BLOB_MISSING, (commit("alice", key()) as CommitBlobUpload.Result.Rejected).reason)

        objects.objects[key()] = ObjectInfo(301_229, mapOf("sha256" to sha))
        assertEquals(BlobRejection.BLOB_MISSING, (commit("alice", key()) as CommitBlobUpload.Result.Rejected).reason)

        objects.objects[key()] = ObjectInfo(301_230, mapOf("sha256" to "f".repeat(64)))
        assertEquals(BlobRejection.BLOB_MISSING, (commit("alice", key()) as CommitBlobUpload.Result.Rejected).reason)

        objects.objects[key()] = ObjectInfo(301_230, mapOf("sha256" to sha))
        assertEquals(CommitBlobUpload.Result.Committed, commit("alice", key()))
    }

    @Test
    fun commitRefusesAnotherAccountsKeyAndUnannouncedUploads() {
        active()
        objects.objects[key("mallory")] = ObjectInfo(1, emptyMap())
        assertEquals(BlobRejection.FORBIDDEN, (commit("alice", key("mallory")) as CommitBlobUpload.Result.Rejected).reason)
        assertEquals(BlobRejection.VALIDATION, (commit("alice", "nope") as CommitBlobUpload.Result.Rejected).reason)
    }
}
