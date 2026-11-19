package com.driveperf.platform.crash

import android.content.Context
import com.driveperf.domain.capture.RecorderStatus
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * [CrashReporter] on Sentry's Android SDK, EU region (ADR-007, DRIVE-196).
 *
 * - Initialised only after crash consent: [setEnabled] `true` calls `SentryAndroid.init`; `false` closes the SDK and
 *   deletes cached envelopes. Auto-init is switched off in the manifest, so nothing runs before consent.
 * - The DSN comes from build configuration and is never committed. With a blank [dsn] this is a no-op stub, so a build
 *   without the Sentry account configured behaves exactly like one with the toggle off.
 * - `sendDefaultPii` is off, no user is ever set, screenshots, view hierarchy and tracing are off. Crashes, ANRs (5 s)
 *   and release health (crash-free sessions, F-ANA-03) are on.
 * - Tags come only from [CrashTag]; [scrub] drops any other tag and every extra, and removes coordinate-like text
 *   from the message, exception values and breadcrumbs.
 */
class SentryCrashReporter(private val context: Context, private val dsn: String, private val appBuild: String) : CrashReporter {
    @Volatile
    private var cacheDir: String? = null

    @Volatile
    private var tripTags: Map<CrashTag, String> = CrashTag.forTrip(RecorderStatus.IDLE)

    override val isAvailable: Boolean get() = dsn.isNotBlank()

    @Synchronized
    @Suppress("DEPRECATION") // sendDefaultPii is deprecated but stays set explicitly: ADR-007 requires it to be false.
    override fun setEnabled(enabled: Boolean) {
        if (!isAvailable) return
        if (enabled) {
            if (Sentry.isEnabled()) return
            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.isSendDefaultPii = false
                options.tracesSampleRate = 0.0
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.isEnableUserInteractionBreadcrumbs = false
                options.isAnrEnabled = true // F-ANA-03: ANRs are reported like crashes
                options.isEnableAutoSessionTracking = true // release health: crash-free sessions (F-ANA-03)
                options.setTag(CrashTag.APP_BUILD.key, appBuild)
                tripTags.forEach { (tag, value) -> options.setTag(tag.key, value) }
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
                options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ -> scrub(breadcrumb) }
                cacheDir = options.cacheDirPath
            }
        } else {
            Sentry.close()
            cacheDir?.let { java.io.File(it).deleteRecursively() } // an opt-out also removes what was queued but not sent
            cacheDir = null
        }
    }

    override fun setTrip(status: RecorderStatus) {
        val next = CrashTag.forTrip(status)
        if (next == tripTags) return
        tripTags = next
        if (Sentry.isEnabled()) Sentry.configureScope { scope -> next.forEach { (tag, value) -> scope.setTag(tag.key, value) } }
    }

    internal companion object {
        /** `beforeSend`: allow-listed tags only, no extras, no coordinate-like text. */
        fun scrub(event: SentryEvent): SentryEvent {
            event.message?.let { message ->
                message.formatted = message.formatted?.let(CoordinateScrubber::scrub)
                message.message = message.message?.let(CoordinateScrubber::scrub)
                message.params = message.params?.map(CoordinateScrubber::scrub)
            }
            event.exceptions?.forEach { exception -> exception.value = exception.value?.let(CoordinateScrubber::scrub) }
            event.tags?.keys?.filterNot { it in CrashTag.allowedKeys }?.forEach(event::removeTag)
            event.extras?.keys?.toList()?.forEach(event::removeExtra)
            event.breadcrumbs = event.breadcrumbs?.mapNotNull(::scrub)
            return event
        }

        /** `beforeBreadcrumb`: a breadcrumb whose message or data looks like a location is dropped. */
        fun scrub(breadcrumb: Breadcrumb): Breadcrumb? {
            val texts = listOfNotNull(breadcrumb.message) + breadcrumb.data.values.map { it.toString() }
            return breadcrumb.takeUnless { texts.any(CoordinateScrubber::containsCoordinates) }
        }
    }
}
