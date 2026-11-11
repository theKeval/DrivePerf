package com.driveperf.platform.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TripIndexTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `the index keeps trips newest first and survives a restart`() {
        val file = File(tmp.root, "trips/index.tsv")
        val index = TripIndex(file)
        index.add(TripEntry("a", 1_000, 60, 1_234.5, 60, 100))
        index.add(TripEntry("b", 5_000, 120, 2_000.0, 120, null))
        assertEquals(listOf("b", "a"), index.entries.value.map { it.tripId })

        val reloaded = TripIndex(file).entries.value
        assertEquals(listOf("b", "a"), reloaded.map { it.tripId })
        assertEquals(null, reloaded[0].gpsDeliveryWhileMovingPercent)
        assertEquals(100, reloaded[1].gpsDeliveryWhileMovingPercent)
        assertEquals(1_234.5, reloaded[1].distanceM, 1e-9)
    }

    @Test
    fun `a damaged index line is skipped, not fatal`() {
        val file = File(tmp.root, "index.tsv")
        file.writeText("garbage\na\t1\t2\t3.0\t4\t5")
        assertEquals(listOf("a"), TripIndex(file).entries.value.map { it.tripId })
    }

    @Test
    fun `a failed save changes nothing and the previous index stays intact`() {
        val file = File(tmp.root, "idx.tsv")
        val index = TripIndex(file)
        assertTrue(index.add(TripEntry("a", 1_000, 60, 1.0, 60, 100)))

        file.delete()
        file.mkdirs() // the index path is now a directory, so the rename over it must fail
        assertFalse(index.add(TripEntry("b", 5_000, 60, 1.0, 60, 100)))
        assertEquals("the in-memory list is unchanged", listOf("a"), index.entries.value.map { it.tripId })
        assertTrue("no temporary file is left behind", tmp.root.list().orEmpty().none { it.endsWith(".tmp") })
    }

    @Test
    fun `a saved index leaves no temporary file`() {
        val file = File(tmp.root, "idx.tsv")
        TripIndex(file).add(TripEntry("a", 1_000, 60, 1.0, 60, 100))
        assertEquals(listOf("idx.tsv"), tmp.root.list().orEmpty().toList())
    }
}
