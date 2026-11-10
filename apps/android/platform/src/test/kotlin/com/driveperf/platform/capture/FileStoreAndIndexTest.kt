package com.driveperf.platform.capture

import com.driveperf.platform.Fixtures
import com.driveperf.platform.Fixtures.fix
import com.driveperf.platform.ports.ActivityEvent
import com.driveperf.platform.ports.ActivityKind
import com.driveperf.platform.ports.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileStoreAndIndexTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `writes a header and one line per sample, with empty fields for nulls`() {
        val store = FileTripFileStore(File(tmp.root, "trips"))
        val writer = store.open("t1")
        writer.append(fix(1.0, speedMps = null))
        writer.append(Fixtures.motion(1.0))
        writer.append(ActivityEvent(5, ActivityKind.VEHICLE, TransitionKind.ENTER))
        writer.flush()
        writer.close()

        val lines = File(tmp.root, "trips/t1.r0").readLines()
        assertEquals("# driveperf-r0-interim v0", lines[0])
        assertTrue(lines[1].startsWith("L,1000000000,1000,"))
        assertEquals(12, lines[1].split(",").size)
        assertTrue("null speed is an empty field", lines[1].split(",")[8].isEmpty())
        assertTrue(lines[2].startsWith("M,1000000000,"))
        assertEquals("A,5,VEHICLE,ENTER", lines[3])
    }

    @Test
    fun `reopening a trip appends without a second header`() {
        val store = FileTripFileStore(tmp.root)
        store.open("t1").apply { append(fix(1.0)); close() }
        store.open("t1").apply { append(fix(2.0)); close() }
        assertEquals(1, File(tmp.root, "t1.r0").readLines().count { it.startsWith("#") })
        assertEquals(2, File(tmp.root, "t1.r0").readLines().count { it.startsWith("L,") })
    }

    @Test
    fun `delete removes the file`() {
        val store = FileTripFileStore(tmp.root)
        store.open("gone").close()
        store.delete("gone")
        assertFalse(File(tmp.root, "gone.r0").exists())
    }

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
}
