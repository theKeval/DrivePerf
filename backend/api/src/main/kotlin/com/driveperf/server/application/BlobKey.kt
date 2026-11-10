package com.driveperf.server.application

/**
 * A trip-file object key: `u/{accountId}/t/{tripId}/r1.v{formatVersion}.{sha256[0:16]}.pb.gz`
 * (data-model.md 3.4, backend/openapi/v1.yaml `BlobKey`). Content-addressed and immutable. Parsing is the only way
 * to get one, so a value of this type is always well formed and its account segment can be compared with the caller.
 */
internal data class BlobKey(
    val value: String,
    val accountId: String,
    val tripId: String,
    val formatVersion: Int,
    val hashPrefix: String,
) {
    companion object {
        private const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val pattern = Regex("^u/($ACCOUNT_ID_CHARS{1,128})/t/($UUID)/r1\\.v([0-9]{1,3})\\.([0-9a-f]{16})\\.pb\\.gz$")

        fun parse(raw: String): BlobKey? = pattern.matchEntire(raw)?.let { m ->
            BlobKey(
                value = raw,
                accountId = m.groupValues[1],
                tripId = m.groupValues[2],
                formatVersion = m.groupValues[3].toInt(),
                hashPrefix = m.groupValues[4],
            )
        }
    }
}
