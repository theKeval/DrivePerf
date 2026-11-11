package com.driveperf.server.application

import com.driveperf.server.ports.AccountRecord
import com.driveperf.server.ports.AccountStatus
import com.driveperf.server.ports.AccountStore
import com.driveperf.server.ports.BackupState
import com.driveperf.server.ports.BlobSigner
import com.driveperf.server.ports.Clock
import com.driveperf.server.ports.ObjectStore
import com.driveperf.server.ports.PendingUpload
import com.driveperf.server.ports.PresignedRequest
import com.driveperf.server.ports.UploadLedger

/** Why a blob request was refused. The route maps these to the `ApiError` codes of backend/openapi/v1.yaml. */
internal enum class BlobRejection { VALIDATION, FORBIDDEN, BACKUP_NOT_ALLOWED, ACCOUNT_DELETING, TOO_LARGE, BLOB_MISSING }

internal data class PrepareBlobRequest(val key: String, val sha256: String, val sizeBytes: Long, val contentType: String)

/**
 * `POST /v1/blobs:prepare`. Checks, in order: the key is well formed and matches the announced hash; the file is not
 * over the limit; the caller owns the account segment of the key; the account is not being deleted; backup is
 * allowed. Then either reports that the object is already stored or mints a presigned PUT whose headers are signed.
 */
internal class PrepareBlobUpload(
    private val accounts: AccountStore,
    private val objects: ObjectStore,
    private val signer: BlobSigner,
    private val ledger: UploadLedger,
    private val clock: Clock,
) {
    sealed interface Result {
        data object AlreadyStored : Result

        data class Upload(val request: PresignedRequest) : Result

        data class Rejected(val reason: BlobRejection) : Result
    }

    operator fun invoke(accountId: String, request: PrepareBlobRequest): Result {
        val key = BlobKey.parse(request.key) ?: return Result.Rejected(BlobRejection.VALIDATION)
        val account = accounts.find(accountId)
        val now = clock.nowEpochMillis()
        val rejection = invalid(key, request) ?: notAllowed(key, accountId, account, now)
        return if (rejection != null) Result.Rejected(rejection) else issue(accountId, key, request)
    }

    private fun invalid(key: BlobKey, request: PrepareBlobRequest): BlobRejection? = when {
        !SHA256.matches(request.sha256) || !request.sha256.startsWith(key.hashPrefix) -> BlobRejection.VALIDATION
        request.contentType != CONTENT_TYPE || request.sizeBytes < 1 -> BlobRejection.VALIDATION
        request.sizeBytes > MAX_BYTES -> BlobRejection.TOO_LARGE
        else -> null
    }

    private fun notAllowed(key: BlobKey, accountId: String, account: AccountRecord?, now: Long): BlobRejection? = when {
        key.accountId != accountId -> BlobRejection.FORBIDDEN
        account?.status == AccountStatus.DELETING -> BlobRejection.ACCOUNT_DELETING
        account == null || account.backupState != BackupState.ACTIVE -> BlobRejection.BACKUP_NOT_ALLOWED
        account.writeUntilEpochMillis?.let { it <= now } == true -> BlobRejection.BACKUP_NOT_ALLOWED
        else -> null
    }

    private fun issue(accountId: String, key: BlobKey, request: PrepareBlobRequest): Result {
        val stored = objects.head(key.value)
        return if (stored != null && stored.sizeBytes == request.sizeBytes && stored.metadata[META_SHA256] == request.sha256) {
            Result.AlreadyStored
        } else {
            val presigned = signer.presignPut(key.value, uploadHeaders(key, request), TTL_SECONDS)
            ledger.record(accountId, PendingUpload(key.value, request.sha256, request.sizeBytes, presigned.expiresAtEpochMillis))
            Result.Upload(presigned)
        }
    }

    private fun uploadHeaders(key: BlobKey, request: PrepareBlobRequest): Map<String, String> = mapOf(
        "Content-Type" to request.contentType,
        "Content-Length" to request.sizeBytes.toString(),
        "x-amz-meta-$META_SHA256" to request.sha256,
        "x-amz-meta-tripid" to key.tripId,
        "x-amz-meta-formatversion" to key.formatVersion.toString(),
    )

    internal companion object {
        const val CONTENT_TYPE = "application/x-protobuf"
        const val MAX_BYTES = 16L * 1024 * 1024
        const val TTL_SECONDS = 15 * 60
        const val META_SHA256 = "sha256"
        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * `POST /v1/blobs:commit`. Reads the stored object's size and recorded hash (HEAD) and compares them with what the
 * device announced in `prepare`. The store cannot check the bytes against the hash without reading them; the device
 * verifies the hash again on restore, and the key itself carries the first 16 hex digits.
 */
internal class CommitBlobUpload(
    private val objects: ObjectStore,
    private val ledger: UploadLedger,
) {
    sealed interface Result {
        data object Committed : Result

        data class Rejected(val reason: BlobRejection) : Result
    }

    operator fun invoke(accountId: String, rawKey: String): Result {
        val key = BlobKey.parse(rawKey)
        val announced = key?.let { ledger.find(accountId, it.value) }
        val stored = key?.let { objects.head(it.value) }
        val rejection = when {
            key == null -> BlobRejection.VALIDATION
            key.accountId != accountId -> BlobRejection.FORBIDDEN
            announced == null || stored == null -> BlobRejection.BLOB_MISSING
            stored.sizeBytes != announced.sizeBytes -> BlobRejection.BLOB_MISSING
            stored.metadata[PrepareBlobUpload.META_SHA256] != announced.sha256 -> BlobRejection.BLOB_MISSING
            else -> null
        }
        return if (rejection == null) Result.Committed else Result.Rejected(rejection)
    }
}
