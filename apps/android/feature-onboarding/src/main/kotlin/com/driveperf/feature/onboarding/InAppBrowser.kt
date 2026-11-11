package com.driveperf.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens a web page in a Custom Tab, inside the app, so the Terms, the Privacy Policy and "How it works" never take the
 * user out of onboarding (F-ONB-02.3, F-ONB-03.3). Only https pages are opened. Without a browser that supports Custom
 * Tabs it falls back to a plain view intent.
 */
object InAppBrowser {
    fun isAllowed(url: String): Boolean = url.toUri().scheme.equals("https", ignoreCase = true)

    fun open(context: Context, url: String) {
        if (!isAllowed(url)) return
        val uri = url.toUri()
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No browser installed: nothing to open. The links are also on driveperf.com.
            }
        }
    }
}
