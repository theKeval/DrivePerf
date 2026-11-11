package com.driveperf.server.ports

/** What the store knows about one object (HEAD). Metadata keys are lower case without the `x-amz-meta-` prefix. */
internal data class ObjectInfo(val sizeBytes: Long, val metadata: Map<String, String>)

/** The S3-compatible bucket for trip files (Cloudflare R2 today, ADR-004). Methods block; callers run them off the event loop. */
internal interface ObjectStore {
    fun head(key: String): ObjectInfo?

    /** At most [limit] keys that start with [prefix], in any order. */
    fun listKeys(prefix: String, limit: Int): List<String>

    /** Deletes up to 1,000 keys. Idempotent: a missing key is not an error. */
    fun deleteKeys(keys: List<String>)
}

/** A request the device must send as is: URL, method and the signed headers. */
internal data class PresignedRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val expiresAtEpochMillis: Long,
)

/** Mints presigned URLs so devices never hold storage credentials (decisions log 6 Nov 2026). */
internal interface BlobSigner {
    /** [headers] are bound into the signature; the device must send exactly these. */
    fun presignPut(objectKey: String, headers: Map<String, String>, ttlSeconds: Int): PresignedRequest

    fun presignGet(objectKey: String, ttlSeconds: Int): PresignedRequest
}

/** What a device announced in `blobs:prepare`, kept until `blobs:commit` can check it against the stored object. */
internal data class PendingUpload(val key: String, val sha256: String, val sizeBytes: Long, val expiresAtEpochMillis: Long)

internal interface UploadLedger {
    fun record(accountId: String, upload: PendingUpload)

    fun find(accountId: String, key: String): PendingUpload?

    /**
     * The latest moment any upload URL issued for [accountId] can still be used, or null when none was issued. Entries
     * must outlive the account's user data and go with the account record, so the deletion cascade can still read this.
     */
    fun latestExpiryEpochMillis(accountId: String): Long?
}
