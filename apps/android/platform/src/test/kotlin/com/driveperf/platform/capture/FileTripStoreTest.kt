package com.driveperf.platform.capture

import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class FileTripStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(unlocked: () -> Boolean = { true }) = FileTripStore(File(tmp.root, "trips"), unlocked)

    private val r0 = TripFileKind.R0

    @Test
    fun `appended bytes are read back in order`() {
        val store = store()
        assertEquals(FileResult.OK, store.append("t1", r0, "ab".toByteArray()))
        assertEquals(FileResult.OK, store.append("t1", r0, "cd".toByteArray()))
        assertEquals(FileResult.OK, store.sync("t1", r0))
        assertArrayEquals("abcd".toByteArray(), store.read("t1", r0))
    }

    @Test
    fun `a reopened store appends to the same file`() {
        store().apply { append("t1", r0, "a".toByteArray()); sync("t1", r0) }
        val second = store()
        second.append("t1", r0, "b".toByteArray())
        assertArrayEquals("ab".toByteArray(), second.read("t1", r0))
    }

    @Test
    fun `write replaces the whole file`() {
        val store = store()
        store.append("t1", r0, "old old old".toByteArray())
        assertEquals(FileResult.OK, store.write("t1", r0, "new".toByteArray()))
        assertArrayEquals("new".toByteArray(), store.read("t1", r0))
        assertFalse("no temporary file is left behind", File(tmp.root, "trips/t1").list().orEmpty().any { it.endsWith(".tmp") })
    }

    @Test
    fun `compress gzips in place and read still returns the plain bytes`() {
        val store = store()
        val payload = "x".repeat(2000).toByteArray()
        store.append("t1", r0, payload)
        assertEquals(FileResult.OK, store.compress("t1", r0))
        assertArrayEquals(payload, store.read("t1", r0))
        val described = store.describe("t1", r0, withSha256 = false)!!
        assertTrue(described.compressed)
        assertTrue("2000 repeated bytes compress well", described.sizeBytes < 100)
        assertNull(described.sha256)
    }

    @Test
    fun `describe returns the size and the hash of the stored bytes`() {
        val store = store()
        val bytes = "hello".toByteArray()
        store.write("t1", TripFileKind.PREVIEW, bytes)
        val described = store.describe("t1", TripFileKind.PREVIEW, withSha256 = true)!!
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(5L, described.sizeBytes)
        assertEquals(expected, described.sha256)
        assertFalse(described.compressed)
    }

    @Test
    fun `a missing file reads as null and deletes successfully`() {
        val store = store()
        assertNull(store.read("nope", r0))
        assertNull(store.describe("nope", r0, withSha256 = true))
        assertEquals(FileResult.OK, store.delete("nope", r0))
        assertEquals(FileResult.OK, store.compress("nope", r0))
    }

    @Test
    fun `deleting a trip removes its folder and its id`() {
        val store = store()
        store.append("t1", r0, "a".toByteArray())
        store.write("t2", TripFileKind.R1, "b".toByteArray())
        assertEquals(listOf("t1", "t2"), store.tripIds())
        assertEquals(FileResult.OK, store.deleteTrip("t1"))
        assertEquals(listOf("t2"), store.tripIds())
        assertFalse(File(tmp.root, "trips/t1").exists())
    }

    @Test
    fun `a trip id that could leave the root is refused`() {
        val store = store()
        assertEquals(FileResult.IO_ERROR, store.append("../escape", r0, "a".toByteArray()))
        assertEquals(FileResult.IO_ERROR, store.append("", r0, "a".toByteArray()))
        assertNull(store.read("../escape", r0))
        assertFalse(File(tmp.root, "escape").exists())
    }

    @Test
    fun `before the first unlock every call answers unavailable`() {
        val store = store(unlocked = { false })
        assertEquals(FileResult.UNAVAILABLE, store.append("t1", r0, "a".toByteArray()))
        assertEquals(FileResult.UNAVAILABLE, store.deleteTrip("t1"))
        assertNull(store.read("t1", r0))
        assertTrue(store.tripIds().isEmpty())
    }

    @Test
    fun `a fresh store lists no trips`() {
        assertTrue(store().tripIds().isEmpty())
    }
}
