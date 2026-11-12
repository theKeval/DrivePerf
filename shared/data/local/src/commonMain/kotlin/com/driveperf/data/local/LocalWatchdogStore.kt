package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.capture.WatchdogState
import com.driveperf.domain.capture.WatchdogStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * [WatchdogStore] on the single `capture_watchdog` row (F-CAP-14). Storage errors stop here (ADR-002 section 7): a
 * full disk or a locked database must not reach the recorder, which keeps recording and keeps the state in memory.
 */
public class LocalWatchdogStore(private val db: DrivePerfDatabase, private val io: CoroutineContext) : WatchdogStore {
    override suspend fun read(): WatchdogState = withContext(io) {
        storage {
            db.tripQueries.watchdog().executeAsOneOrNull()?.let { WatchdogState(it.last_check_in_at, it.due_at, it.last_fired_at) }
        } ?: WatchdogState()
    }

    override suspend fun write(state: WatchdogState): Boolean = withContext(io) {
        storage { db.tripQueries.setWatchdog(state.lastCheckInWallMs, state.dueWallMs, state.lastFiredWallMs) } != null
    }

    /**
     * Runs [block]; null when the database throws. SQLDelight drivers throw different types per platform (JDBC
     * `SQLException`, Android `SQLiteException`, native `SQLiteException`), so the boundary catches them all and
     * always rethrows cancellation.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun <T> storage(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    }
}
