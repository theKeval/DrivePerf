package com.driveperf.platform.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

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
class TripIndex(internal val file: File) {
    private val state = MutableStateFlow(load())

    /** Newest first. */
    val entries: StateFlow<List<TripEntry>> get() = state

    /**
     * Adds [entry] and persists the list by atomic replacement (temporary file, sync, rename), so an interrupted write
     * leaves the previous index intact. Returns false, and changes nothing, when the list could not be saved.
     */
    @Synchronized
    fun add(entry: TripEntry): Boolean {
        val updated = (listOf(entry) + state.value).sortedByDescending { it.startedWallMs }
        return try {
            replaceAtomically(file, updated.joinToString("\n") { it.encode() }.toByteArray())
            state.value = updated
            true
        } catch (_: IOException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun load(): List<TripEntry> =
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { it.decode() }

    private fun TripEntry.encode() = listOf(
        tripId, startedWallMs, durationS, distanceM, fixCount, gpsDeliveryWhileMovingPercent ?: "",
    ).joinToString("\t")

    private fun String.decode(): TripEntry? {
        val f = split("\t")
        if (f.size < FIELD_COUNT) return null
        // A damaged line is skipped rather than failing the whole list.
        return runCatching { TripEntry(f[0], f[1].toLong(), f[2].toLong(), f[3].toDouble(), f[4].toInt(), f[5].toIntOrNull()) }
            .getOrNull()
    }

    private companion object {
        const val FIELD_COUNT = 6
    }
}
