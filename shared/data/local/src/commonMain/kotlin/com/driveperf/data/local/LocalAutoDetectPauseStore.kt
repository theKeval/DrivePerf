package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.capture.AutoDetectPause
import com.driveperf.domain.capture.AutoDetectPauseLength
import com.driveperf.domain.capture.AutoDetectPauseStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AutoDetectPauseStore] on the single `capture_pause` row (F-CAP-15): a row while paused, none otherwise. Storage
 * errors stop here (ADR-002 section 7), as in [LocalWatchdogStore]: the recorder keeps the pause in memory. A row with
 * a length this version does not know reads as no pause, so a downgrade never leaves detection stuck off.
 */
public class LocalAutoDetectPauseStore(private val db: DrivePerfDatabase, private val io: CoroutineContext) :
    AutoDetectPauseStore {
    override suspend fun read(): AutoDetectPause? = withContext(io) {
        storage {
            db.tripQueries.autoDetectPause().executeAsOneOrNull()?.let { row ->
                AutoDetectPauseLength.entries.firstOrNull { it.name == row.length }
                    ?.let { AutoDetectPause(it, row.paused_at, row.until_at) }
            }
        }
    }

    override suspend fun write(pause: AutoDetectPause?): Boolean = withContext(io) {
        storage {
            if (pause == null) {
                db.tripQueries.clearAutoDetectPause()
            } else {
                db.tripQueries.setAutoDetectPause(pause.length.name, pause.pausedAtWallMs, pause.untilWallMs)
            }
        } != null
    }

    /** Runs [block]; null when the database throws (see [LocalWatchdogStore]). Always rethrows cancellation. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun <T> storage(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    }
}
