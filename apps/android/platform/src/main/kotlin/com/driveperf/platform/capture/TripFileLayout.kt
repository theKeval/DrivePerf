package com.driveperf.platform.capture

import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.TripFileKind
import java.io.File

/**
 * Where trip files live: `<root>/<tripId>/<name>`, with `<name>.gz` once compressed (data-model.md section 2.1). A trip id
 * becomes a folder name, so anything that could climb out of [root] is refused by [validId].
 */
internal class TripFileLayout(val root: File, private val isUnlocked: () -> Boolean) {
    /** True when storage is readable (after the first unlock) and [tripId] is safe to use in a path. */
    fun usable(tripId: String) = isUnlocked() && validId(tripId)

    fun unlocked() = isUnlocked()

    fun validId(tripId: String) = tripId.isNotEmpty() && tripId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /** Runs a file operation on a trip, answering `UNAVAILABLE` before unlock and `IO_ERROR` for an unsafe id. */
    inline fun guarded(tripId: String, action: () -> Unit): FileResult = when {
        !unlocked() -> FileResult.UNAVAILABLE
        !validId(tripId) -> FileResult.IO_ERROR
        else -> fileResult(action)
    }

    fun dir(tripId: String) = File(root, tripId)

    fun plain(tripId: String, kind: TripFileKind) = File(dir(tripId), fileName(kind))

    fun gz(tripId: String, kind: TripFileKind) = File(dir(tripId), fileName(kind) + GZ_SUFFIX)

    private fun fileName(kind: TripFileKind) = when (kind) {
        TripFileKind.R0 -> "r0.v1.pb"
        TripFileKind.R1 -> "r1.v1.pb"
        TripFileKind.PREVIEW -> "preview.pb"
    }

    companion object {
        const val GZ_SUFFIX = ".gz"
        const val TEMP_SUFFIX = ".tmp"
    }
}
