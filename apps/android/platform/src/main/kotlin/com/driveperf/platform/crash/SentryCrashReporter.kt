package com.driveperf.platform.crash

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * [CrashReporter] on Sentry's Android SDK, EU region (ADR-007).
 *
 * - Initialised only after crash consent: [setEnabled] `true` calls `SentryAndroid.init`; `false` closes the SDK and
 *   deletes cached envelopes. Auto-init is switched off in the manifest, so nothing runs before consent.
 * - The DSN comes from build configuration and is never committed. With a blank [dsn] this is a no-op stub, so a build
 *   without the Sentry account configured behaves exactly like one with the toggle off.
 * - `sendDefaultPii` is off, no user is ever set, screenshots, view hierarchy and tracing are off, and every event and
 *   breadcrumb passes the [CoordinateScrubber].
 */
class SentryCrashReporter(private val context: Context, private val dsn: String, private val appBuild: String) : CrashReporter {
    @Volatile
    private var cacheDir: String? = null

    /** True when a DSN is configured, so the Settings screen can say whether crash reports can be sent at all. */
    val isConfigured: Boolean get() = dsn.isNotBlank()

    @Synchronized
    @Suppress("DEPRECATION") // sendDefaultPii is deprecated but stays set explicitly: ADR-007 requires it to be false.
    override fun setEnabled(enabled: Boolean) {
        if (!isConfigured) return
        if (enabled) {
            if (Sentry.isEnabled()) return
            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.isSendDefaultPii = false
                options.tracesSampleRate = 0.0
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.isEnableUserInteractionBreadcrumbs = false
                options.isEnableAutoSessionTracking = true // release health: crash-free sessions (F-ANA-03)
                options.setTag("app_build", appBuild)
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrubEvent(event) }
                options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                    if (CoordinateScrubber.containsCoordinates(breadcrumb.message)) null else breadcrumb
                }
                cacheDir = options.cacheDirPath
            }
        } else {
            Sentry.close()
            cacheDir?.let { java.io.File(it).deleteRecursively() } // an opt-out also removes what was queued but not sent
            cacheDir = null
        }
    }

    private fun scrubEvent(event: io.sentry.SentryEvent): io.sentry.SentryEvent {
        event.message?.formatted?.let { event.message?.formatted = CoordinateScrubber.scrub(it) }
        return event
    }
}
