package com.driveperf.domain.tripfile

/** The files kept per trip (data-model.md §2.1). */
public enum class TripFileKind {
    /** Capture log: device frame, 50 Hz IMU and every fix. Appended while recording, never uploaded. */
    R0,

    /** Analysis file: vehicle frame at 25 Hz plus 1 Hz GNSS. Written once at finalisation; Pro backup uploads it. */
    R1,

    /** Preview track: at most 400 rows for lists and maps. */
    PREVIEW,
}

/** The outcome of a file operation. Codes only, so Swift can implement the port without Kotlin types. */
public enum class FileResult {
    OK,

    /** Not enough free space (EC-STO). */
    DISK_FULL,

    /** The file is protected and the phone has not been unlocked since boot. */
    UNAVAILABLE,

    /** Any other I/O failure. */
    IO_ERROR,
}

/**
 * What is stored for one file.
 *
 * @property sha256 lower-case hex, or null when it was not requested.
 */
public data class StoredFile(val sizeBytes: Long, val sha256: String?, val compressed: Boolean)

/**
 * Byte storage for trip files under `trips/<tripId>/` in app-private storage (ADR-003, data-model.md §2.1). The
 * format (magic, header, length-delimited chunks) is the core's job; this port only stores bytes with the right
 * protection: iOS `.completeUnlessOpen` for R0 and R1 so recording works while locked, R0 excluded from device
 * backup; Android credential-encrypted `filesDir`.
 *
 * Calls are synchronous and come from the core's confined capture thread, never the main thread.
 */
public interface TripFileStore {
    /** Appends [bytes] to the end of the file, creating it if needed. Buffered: call [sync] to make it durable. */
    public fun append(tripId: String, kind: TripFileKind, bytes: ByteArray): FileResult

    /** Flushes appended bytes to disk (`fsync`). The core calls it at most every 5 s while recording. */
    public fun sync(tripId: String, kind: TripFileKind): FileResult

    /** Replaces the whole file atomically (write to a temporary file, then rename). */
    public fun write(tripId: String, kind: TripFileKind, bytes: ByteArray): FileResult

    /**
     * Gzip-compresses the file in place (RFC 1952) when the trip is finalised. [read] keeps returning the
     * uncompressed bytes afterwards.
     */
    public fun compress(tripId: String, kind: TripFileKind): FileResult

    /** The file's uncompressed bytes, or null when it does not exist or cannot be read. */
    public fun read(tripId: String, kind: TripFileKind): ByteArray?

    /**
     * Size and, when [withSha256] is true, the SHA-256 of the stored bytes (compressed once compressed). Null when
     * the file does not exist. Hashing reads the whole file, so the core asks for it only at finalisation.
     */
    public fun describe(tripId: String, kind: TripFileKind, withSha256: Boolean): StoredFile?

    /** Deletes one file. Deleting a missing file succeeds. */
    public fun delete(tripId: String, kind: TripFileKind): FileResult

    /** Deletes every file of the trip and its folder. */
    public fun deleteTrip(tripId: String): FileResult

    /** IDs of every trip folder on disk, for the startup consistency check (ADR-003). */
    public fun tripIds(): List<String>

    /** Free space on the volume, for the 100 MB EC-STO rule. */
    public fun freeSpaceBytes(): Long
}
