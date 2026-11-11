package com.driveperf.server.adapters

import com.driveperf.server.ports.BlobSigner
import com.driveperf.server.ports.Clock
import com.driveperf.server.ports.PresignedRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 query-string presigning for any S3-compatible store (Cloudflare R2, B2, a self-hosted
 * store), written against the published algorithm with no SDK so the same class moves to a VPS unchanged (ADR-004).
 * It needs no network. Credentials come from Secret Manager at start-up; this class never logs or exposes them.
 *
 * [host] is the endpoint host (R2: `<accountId>.r2.cloudflarestorage.com`). [pathPrefix] is `/<bucket>` for
 * path-style addressing (R2) or empty for virtual-hosted style. [region] is `auto` for R2.
 */
internal class S3PresignedUrlSigner(
    private val host: String,
    private val pathPrefix: String,
    private val region: String,
    private val credentials: Credentials,
    private val clock: Clock,
) : BlobSigner {
    class Credentials(val accessKeyId: String, val secretAccessKey: String) {
        override fun toString(): String = "Credentials(accessKeyId=$accessKeyId, secretAccessKey=***)"
    }

    override fun presignPut(objectKey: String, headers: Map<String, String>, ttlSeconds: Int): PresignedRequest =
        presign("PUT", objectKey, headers, ttlSeconds)

    override fun presignGet(objectKey: String, ttlSeconds: Int): PresignedRequest =
        presign("GET", objectKey, emptyMap(), ttlSeconds)

    private fun presign(method: String, objectKey: String, headers: Map<String, String>, ttlSeconds: Int): PresignedRequest {
        val nowMillis = clock.nowEpochMillis()
        val amzDate = TIMESTAMP.format(java.time.Instant.ofEpochMilli(nowMillis))
        val scope = "${amzDate.substring(0, DATE_LENGTH)}/$region/$SERVICE/aws4_request"

        val signed = (headers.mapKeys { it.key.lowercase() } + ("host" to host)).toSortedMap()
        val signedHeaderNames = signed.keys.joinToString(";")
        val query = mapOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "${credentials.accessKeyId}/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to ttlSeconds.toString(),
            "X-Amz-SignedHeaders" to signedHeaderNames,
        ).entries.map { encode(it.key) to encode(it.value) }.sortedBy { it.first }
        val canonicalQuery = query.joinToString("&") { "${it.first}=${it.second}" }
        val canonicalUri = "$pathPrefix/" + objectKey.split('/').joinToString("/") { encode(it) }
        val canonicalHeaders = signed.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" }

        val canonicalRequest = listOf(method, canonicalUri, canonicalQuery, canonicalHeaders, signedHeaderNames, UNSIGNED_PAYLOAD)
            .joinToString("\n")
        val stringToSign = listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest)).joinToString("\n")
        val signature = hex(hmac(signingKey(amzDate.substring(0, DATE_LENGTH)), stringToSign))

        return PresignedRequest(
            url = "https://$host$canonicalUri?$canonicalQuery&X-Amz-Signature=$signature",
            method = method,
            headers = signed.filterKeys { it != "host" },
            expiresAtEpochMillis = nowMillis + ttlSeconds * MILLIS_PER_SECOND,
        )
    }

    private fun signingKey(date: String): ByteArray {
        val kDate = hmac("AWS4${credentials.secretAccessKey}".toByteArray(StandardCharsets.UTF_8), date)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, SERVICE)
        return hmac(kService, "aws4_request")
    }

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data.toByteArray(StandardCharsets.UTF_8))

    private fun sha256Hex(data: String): String =
        hex(java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray(StandardCharsets.UTF_8)))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** RFC 3986 encoding as SigV4 requires: unreserved characters stay, everything else is %XX. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private companion object {
        const val ALGORITHM = "AWS4-HMAC-SHA256"
        const val SERVICE = "s3"
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        const val DATE_LENGTH = 8
        const val MILLIS_PER_SECOND = 1000L
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
