package com.driveperf.server

import com.driveperf.server.adapters.S3PresignedUrlSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class S3PresignedUrlSignerTest {
    // Credentials, date and expected URL are the worked example of the AWS Signature V4 documentation
    // ("Authenticating Requests: Using Query Parameters"), so this proves the algorithm byte for byte.
    private val awsExampleCredentials =
        S3PresignedUrlSigner.Credentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    private val awsExampleNow = 1_369_353_600_000L // 2013-05-24T00:00:00Z

    @Test
    fun matchesTheAwsDocumentedExample() {
        val signer = S3PresignedUrlSigner(
            "examplebucket.s3.amazonaws.com", "", "us-east-1", awsExampleCredentials, FakeClock(awsExampleNow),
        )

        val presigned = signer.presignGet("test.txt", 86_400)

        assertEquals(
            "https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host" +
                "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404",
            presigned.url,
        )
        assertEquals(awsExampleNow + 86_400_000, presigned.expiresAtEpochMillis)
    }

    private fun r2(clock: FakeClock = FakeClock(awsExampleNow)) =
        S3PresignedUrlSigner("acct.r2.cloudflarestorage.com", "/driveperf-trips-dev", "auto", awsExampleCredentials, clock)

    private val headers = mapOf("Content-Type" to "application/x-protobuf", "Content-Length" to "301230", "x-amz-meta-sha256" to "ab")
    private val key = "u/alice/t/0193f7a2-5b1e-7c3d-9a10-4f2e8b6c1d20/r1.v1.9f2c41aa07be3d55.pb.gz"

    @Test
    fun putUsesPathStyleAndSignsTheBoundHeaders() {
        val presigned = r2().presignPut(key, headers, 900)

        assertTrue(presigned.url.startsWith("https://acct.r2.cloudflarestorage.com/driveperf-trips-dev/$key?"))
        assertTrue("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost%3Bx-amz-meta-sha256" in presigned.url)
        assertEquals(setOf("content-type", "content-length", "x-amz-meta-sha256"), presigned.headers.keys)
        assertEquals("PUT", presigned.method)
    }

    @Test
    fun anyChangeToTheBoundHeadersOrTheKeyChangesTheSignature() {
        val base = r2().presignPut(key, headers, 900).url
        assertNotEquals(base, r2().presignPut(key, headers + ("Content-Length" to "301231"), 900).url)
        assertNotEquals(base, r2().presignPut(key, headers + ("x-amz-meta-sha256" to "ac"), 900).url)
        assertNotEquals(base, r2().presignPut(key.replace("alice", "bobby"), headers, 900).url)
        assertNotEquals(base, r2().presignPut(key, headers, 901).url)
        assertNotEquals(base, r2(FakeClock(awsExampleNow + 1000)).presignPut(key, headers, 900).url)
    }

    @Test
    fun theSecretNeverAppearsInTheUrlOrInToString() {
        val presigned = r2().presignPut(key, headers, 900)
        assertFalse(awsExampleCredentials.secretAccessKey in presigned.url)
        assertFalse(awsExampleCredentials.secretAccessKey in awsExampleCredentials.toString())
    }
}
