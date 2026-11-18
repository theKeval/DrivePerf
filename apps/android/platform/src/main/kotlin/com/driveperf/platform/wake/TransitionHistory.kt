package com.driveperf.platform.wake

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.platform.AndroidClock
import com.driveperf.platform.activity.ActivityLog

/** Play transitions have no query API: retain the 15-minute window across process death, never across a reboot. */
internal class TransitionHistory(context: Context) {
    private val prefs = context.getSharedPreferences("wake_activity_history", Context.MODE_PRIVATE)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun read(nowWallMs: Long): List<ActivitySample> {
        if (boot < 0 || prefs.getInt("boot", -1) != boot) return emptyList()
        return prefs.getStringSet("samples", emptySet()).orEmpty().mapNotNull { row ->
            val c = row.split(',')
            val mono = c.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val wall = c.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val type = ActivityType.entries.firstOrNull { it.name == c.getOrNull(2) } ?: return@mapNotNull null
            if (mono < 0 || wall !in (nowWallMs - HISTORY_MS)..nowWallMs) return@mapNotNull null
            ActivitySample(mono, wall, type, ActivityConfidence.HIGH)
        }.sortedBy { it.tMonoNs }
    }

    fun add(samples: List<ActivitySample>, nowWallMs: Long) {
        val kept = (read(nowWallMs) + samples).filter { it.wallMs in (nowWallMs - HISTORY_MS)..nowWallMs }
            .distinct().sortedBy { it.tMonoNs }.takeLast(CAPACITY)
        prefs.edit {
            putInt("boot", boot)
            putStringSet("samples", kept.map { "${it.tMonoNs},${it.wallMs},${it.type.name}" }.toSet())
        }
    }

    fun restore() {
        read(AndroidClock.wallMs()).forEach(ActivityLog::add)
    }

    private companion object {
        const val HISTORY_MS = 900_000L
        const val CAPACITY = 256
    }
}
