package com.driveperf.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Reads the current runtime permission state from the system. */
class PermissionReader(private val context: Context) {
    fun read(): PermissionState {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return PermissionState(
            fineLocation = fine,
            approximateOnly = coarse && !fine,
            backgroundLocation = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            activityRecognition = granted(Manifest.permission.ACTIVITY_RECOGNITION),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
