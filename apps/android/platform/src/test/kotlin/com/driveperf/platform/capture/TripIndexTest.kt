package com.driveperf.platform.capture

import org.junit.Assert.assertEquals
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
}
