package com.driveperf.platform.capture

import com.driveperf.domain.tripfile.FileResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

private const val BUFFER_BYTES = 8192
private const val NO_SPACE_MARKER = "ENOSPC"

/** Runs [action] and turns an I/O failure into the [FileResult] code the port promises, never an exception. */
internal inline fun fileResult(action: () -> Unit): FileResult = try {
    action()
    FileResult.OK
} catch (e: IOException) {
    if (e.message.orEmpty().contains(NO_SPACE_MARKER, ignoreCase = true)) FileResult.DISK_FULL else FileResult.IO_ERROR
} catch (_: IllegalStateException) {
    FileResult.IO_ERROR
}

/** Writes [bytes] to a temporary file next to [target], syncs it, and renames it over [target]. */
internal fun replaceAtomically(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, target.name + TripFileLayout.TEMP_SUFFIX)
    try {
        FileOutputStream(temporary).use {
            it.write(bytes)
            it.fd.sync()
        }
        check(temporary.renameTo(target)) { "rename failed" }
    } finally {
        temporary.delete() // gone already after a successful rename; otherwise do not leave it behind
    }
}

/** Gzips [source] into [target] (RFC 1952) through a synced temporary file. */
internal fun gzipAtomically(source: File, target: File) {
    val temporary = File(target.parentFile, target.name + TripFileLayout.TEMP_SUFFIX)
    try {
        FileOutputStream(temporary).use { raw ->
            val zip = GZIPOutputStream(raw)
            source.inputStream().use { it.copyTo(zip) }
            zip.finish() // write the gzip trailer; the file descriptor must stay open for the sync below
            raw.fd.sync()
        }
        check(temporary.renameTo(target)) { "rename failed" }
    } finally {
        temporary.delete()
    }
}

/** Lower-case hex SHA-256 of the file's bytes. */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
