package com.driveperf.platform.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** One recorded trip as the trip list needs it. [gpsDeliveryWhileMovingPercent] is null when the trip never moved. */
data class TripEntry(
    val tripId: String,
    val startedWallMs: Long,
    val durationS: Long,
    val distanceM: Double,
    val fixCount: Int,
    val gpsDeliveryWhileMovingPercent: Int?,
)

/**
 * The list of recorded trips, persisted as one tab-separated line each. INTERIM (DRIVE-72): DRIVE-62's `TripRepository`
 * (SQLDelight) replaces it, and "processed" trips with scores replace these raw entries.
 */
class TripIndex(private val file: File) {
    private val state = MutableStateFlow(load())

    /** Newest first. */
    val entries: StateFlow<List<TripEntry>> get() = state

    @Synchronized
    fun add(entry: TripEntry) {
        val updated = (listOf(entry) + state.value).sortedByDescending { it.startedWallMs }
        file.parentFile?.mkdirs()
        file.writeText(updated.joinToString("\n") { it.encode() })
        state.value = updated
    }

    private fun load(): List<TripEntry> =
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { it.decode() }

    private fun TripEntry.encode() = listOf(
        tripId, startedWallMs, durationS, distanceM, fixCount, gpsDeliveryWhileMovingPercent ?: "",
    ).joinToString("\t")

    private fun String.decode(): TripEntry? {
        val f = split("\t")
        if (f.size < FIELD_COUNT) return null
        return TripEntry(
            tripId = f[0],
            startedWallMs = f[1].toLongOrNull() ?: return null,
            durationS = f[2].toLongOrNull() ?: return null,
            distanceM = f[3].toDoubleOrNull() ?: return null,
            fixCount = f[4].toIntOrNull() ?: return null,
            gpsDeliveryWhileMovingPercent = f[5].toIntOrNull(),
        )
    }

    private companion object {
        const val FIELD_COUNT = 6
    }
}
