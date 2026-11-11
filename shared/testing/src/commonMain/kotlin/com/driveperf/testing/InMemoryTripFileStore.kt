package com.driveperf.testing

import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.StoredFile
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore

/**
 * A [TripFileStore] in memory. [sync] marks the appended bytes durable; [crash] drops what was not synced, the way
 * a kill loses the unflushed tail.
 */
@Suppress("TooManyFunctions") // the port's ten operations plus the crash hook
public class InMemoryTripFileStore(public var freeBytes: Long = Long.MAX_VALUE) : TripFileStore {
    private val files = mutableMapOf<Pair<String, TripFileKind>, MutableList<Byte>>()
    private val durable = mutableMapOf<Pair<String, TripFileKind>, Int>()
    private val compressed = mutableSetOf<Pair<String, TripFileKind>>()

    /** Number of [sync] calls, to check fsync cadence. */
    public var syncCount: Int = 0
        private set

    override fun append(tripId: String, kind: TripFileKind, bytes: ByteArray): FileResult {
        if (freeBytes < bytes.size) return FileResult.DISK_FULL
        files.getOrPut(tripId to kind) { mutableListOf() }.addAll(bytes.toList())
        return FileResult.OK
    }

    override fun sync(tripId: String, kind: TripFileKind): FileResult {
        syncCount++
        durable[tripId to kind] = files[tripId to kind]?.size ?: 0
        return FileResult.OK
    }

    override fun write(tripId: String, kind: TripFileKind, bytes: ByteArray): FileResult {
        files[tripId to kind] = bytes.toMutableList()
        durable[tripId to kind] = bytes.size
        return FileResult.OK
    }

    override fun compress(tripId: String, kind: TripFileKind): FileResult {
        if (tripId to kind !in files) return FileResult.IO_ERROR
        compressed += tripId to kind
        return FileResult.OK
    }

    override fun read(tripId: String, kind: TripFileKind): ByteArray? = files[tripId to kind]?.toByteArray()

    override fun describe(tripId: String, kind: TripFileKind, withSha256: Boolean): StoredFile? =
        files[tripId to kind]?.let { bytes ->
            val hash = if (withSha256) "fake-" + bytes.hashCode().toUInt().toString(HEX) else null
            StoredFile(bytes.size.toLong(), hash, tripId to kind in compressed)
        }

    override fun delete(tripId: String, kind: TripFileKind): FileResult {
        files.remove(tripId to kind)
        durable.remove(tripId to kind)
        compressed.remove(tripId to kind)
        return FileResult.OK
    }

    override fun deleteTrip(tripId: String): FileResult {
        TripFileKind.entries.forEach { delete(tripId, it) }
        return FileResult.OK
    }

    override fun tripIds(): List<String> = files.keys.map { it.first }.distinct().sorted()

    override fun freeSpaceBytes(): Long = freeBytes

    /** Simulates a kill: every file loses the bytes appended after its last [sync]. */
    public fun crash() {
        files.keys.toList().forEach { key ->
            val keep = durable[key] ?: 0
            val bytes = files.getValue(key)
            while (bytes.size > keep) bytes.removeAt(bytes.lastIndex)
        }
    }

    private companion object {
        const val HEX = 16
    }
}
