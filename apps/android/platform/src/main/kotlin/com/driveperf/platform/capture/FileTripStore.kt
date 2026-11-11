package com.driveperf.platform.capture

import android.os.StatFs
import com.driveperf.domain.tripfile.FileResult
import com.driveperf.domain.tripfile.StoredFile
import com.driveperf.domain.tripfile.TripFileKind
import com.driveperf.domain.tripfile.TripFileStore
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * [TripFileStore] on app-private credential-encrypted storage: `<root>/<tripId>/<file>` ([TripFileLayout]). [root] is
 * `filesDir/trips`. A trip folder holds `r0.v1.pb`, `r1.v1.pb` and `preview.pb`; compressing a file renames it to
 * `<name>.gz`, and [read] unzips it so callers never see the difference.
 *
 * Calls come from the core's one capture thread, but every method is synchronised so a stray caller cannot corrupt an
 * open stream. Nothing throws: failures come back as [FileResult] codes.
 *
 * [isUnlocked] reports whether credential-encrypted storage is readable; before the first unlock after a reboot every
 * call answers [FileResult.UNAVAILABLE] (EC-RBT) rather than failing in a confusing way.
 */
class FileTripStore(root: File, isUnlocked: () -> Boolean = { true }) : TripFileStore {
    private val layout = TripFileLayout(root, isUnlocked)
    private val appends = AppendStreams()

    @Synchronized
    override fun append(tripId: String, kind: TripFileKind, bytes: ByteArray): FileResult =
        layout.guarded(tripId) { appends.append(layout.plain(tripId, kind), bytes) }

    @Synchronized
    override fun sync(tripId: String, kind: TripFileKind): FileResult = layout.guarded(tripId) { appends.sync(layout.plain(tripId, kind)) }

    @Synchronized
    override fun write(tripId: String, kind: TripFileKind, bytes: ByteArray): FileResult = layout.guarded(tripId) {
        appends.close(layout.plain(tripId, kind))
        layout.gz(tripId, kind).delete()
        replaceAtomically(layout.plain(tripId, kind), bytes)
    }

    @Synchronized
    override fun compress(tripId: String, kind: TripFileKind): FileResult = layout.guarded(tripId) {
        val plain = layout.plain(tripId, kind)
        appends.close(plain)
        if (plain.exists()) {
            gzipAtomically(plain, layout.gz(tripId, kind))
            plain.delete()
        }
    }

    @Synchronized
    override fun read(tripId: String, kind: TripFileKind): ByteArray? {
        if (!layout.usable(tripId)) return null
        val plain = layout.plain(tripId, kind)
        appends.closeQuietly(plain) // reads must see appended bytes
        val gz = layout.gz(tripId, kind)
        return try {
            when {
                gz.exists() -> GZIPInputStream(gz.inputStream()).use { it.readBytes() }
                plain.exists() -> plain.readBytes()
                else -> null
            }
        } catch (_: IOException) {
            null
        }
    }

    @Synchronized
    override fun describe(tripId: String, kind: TripFileKind, withSha256: Boolean): StoredFile? {
        if (!layout.usable(tripId)) return null
        val plain = layout.plain(tripId, kind)
        appends.closeQuietly(plain)
        val gz = layout.gz(tripId, kind)
        val stored = if (gz.exists()) gz else plain.takeIf { it.exists() }
        return stored?.let { StoredFile(it.length(), if (withSha256) sha256Hex(it) else null, compressed = it == gz) }
    }

    @Synchronized
    override fun delete(tripId: String, kind: TripFileKind): FileResult = layout.guarded(tripId) {
        appends.close(layout.plain(tripId, kind))
        layout.plain(tripId, kind).delete()
        layout.gz(tripId, kind).delete()
    }

    @Synchronized
    override fun deleteTrip(tripId: String): FileResult = layout.guarded(tripId) {
        TripFileKind.entries.forEach { appends.close(layout.plain(tripId, it)) }
        layout.dir(tripId).deleteRecursively()
    }

    @Synchronized
    override fun tripIds(): List<String> =
        if (!layout.unlocked()) emptyList() else layout.root.listFiles { f -> f.isDirectory }?.map { it.name }.orEmpty().sorted()

    override fun freeSpaceBytes(): Long {
        layout.root.mkdirs()
        return StatFs(layout.root.path).availableBytes
    }
}
