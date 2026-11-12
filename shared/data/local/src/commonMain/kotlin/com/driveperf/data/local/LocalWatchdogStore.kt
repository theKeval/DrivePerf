package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.capture.WatchdogState
import com.driveperf.domain.capture.WatchdogStore
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** [WatchdogStore] on the single `capture_watchdog` row (F-CAP-14). */
public class LocalWatchdogStore(private val db: DrivePerfDatabase, private val io: CoroutineContext) : WatchdogStore {
    override suspend fun read(): WatchdogState = withContext(io) {
        db.tripQueries.watchdog().executeAsOneOrNull()
            ?.let { WatchdogState(it.last_check_in_at, it.due_at, it.last_fired_at) }
            ?: WatchdogState()
    }

    override suspend fun write(state: WatchdogState): Unit = withContext(io) {
        db.tripQueries.setWatchdog(state.lastCheckInWallMs, state.dueWallMs, state.lastFiredWallMs)
    }
}
