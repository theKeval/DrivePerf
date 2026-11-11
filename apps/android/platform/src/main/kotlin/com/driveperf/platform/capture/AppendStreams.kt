package com.driveperf.platform.capture

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The buffered append streams [FileTripStore] keeps open between calls, one per file. Not thread-safe: the store locks. */
internal class AppendStreams {
    private val open = HashMap<File, Open>()

    fun append(file: File, bytes: ByteArray) {
        val target = open.getOrPut(file) {
            file.parentFile?.mkdirs()
            val stream = FileOutputStream(file, true)
            Open(stream, BufferedOutputStream(stream, BUFFER_BYTES))
        }
        target.out.write(bytes)
    }

    fun sync(file: File) {
        open[file]?.let {
            it.out.flush()
            it.stream.fd.sync()
        }
    }

    /** Flushes, syncs and closes the stream of [file], if one is open. The descriptor is released even if a step throws. */
    fun close(file: File) {
        val target = open.remove(file) ?: return
        try {
            target.out.flush()
            target.stream.fd.sync()
        } finally {
            target.stream.close() // release the descriptor even when the flush failed (disk full)
        }
    }

    /** As [close], for readers: a failure here shows up on the next append or sync, so it is not raised twice. */
    fun closeQuietly(file: File) {
        try {
            close(file)
        } catch (_: IOException) {
            open.remove(file)
        }
    }

    private class Open(val stream: FileOutputStream, val out: BufferedOutputStream)

    private companion object {
        const val BUFFER_BYTES = 8192
    }
}
