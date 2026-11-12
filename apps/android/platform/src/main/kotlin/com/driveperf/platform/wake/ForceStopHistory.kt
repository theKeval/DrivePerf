package com.driveperf.platform.wake

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi

/** Android can report a force-stop only after the process starts again. */
object ForceStopHistory {
    fun detected(context: Context, sinceMs: Long): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val startFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) fromStart(manager) else null
        return startFlag ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && fromExit(manager, context.packageName, sinceMs))
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun fromStart(manager: ActivityManager): Boolean? =
        manager.getHistoricalProcessStartReasons(1).firstOrNull()?.takeIf { it.pid == Process.myPid() }?.wasForceStopped()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun fromExit(manager: ActivityManager, packageName: String, sinceMs: Long): Boolean {
        val exit = manager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return false
        return exit.timestamp > sinceMs && matchesForceStop(exit.reason, exit.description, packageName)
    }

    // shortcut: the AOSP exit description is best-effort on API 30–34, use the public start flag when available.
    // REASON_USER_REQUESTED alone also covers Recents swipes and, on older releases, app updates.
    @RequiresApi(Build.VERSION_CODES.R)
    internal fun matchesForceStop(reason: Int, description: String?, packageName: String): Boolean {
        if (reason != ApplicationExitInfo.REASON_USER_REQUESTED || description == null) return false
        return description.startsWith("[FORCE STOP]", ignoreCase = true) ||
            description.startsWith("stop $packageName due to from pid ")
    }
}
