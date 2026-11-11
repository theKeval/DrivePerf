package com.driveperf.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens a guide step's settings screen. Brand screens are not part of Android's API and change between releases, so each
 * link is tried in order and the last resort is always a system page that exists: DrivePerf's App info (and from there
 * Battery) or the battery-optimisation list. Never `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: that is OQ-12
 * ([BatteryExemptionPolicy]).
 */
object GuideLauncher {
    fun open(context: Context, step: GuideStep) {
        val attempts = step.links.map { intentFor(context, it) } + intentFor(context, GuideLink.AppDetails)
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // This brand screen does not exist on this release: try the next one.
            } catch (_: SecurityException) {
                // The brand screen is not exported to other apps on this release: try the next one.
            }
        }
    }

    fun intentFor(context: Context, link: GuideLink): Intent = when (link) {
        is GuideLink.Component -> Intent().setComponent(ComponentName(link.packageName, link.className))
        GuideLink.BatteryOptimisationSettings -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        GuideLink.AppDetails -> appDetails(context)
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
