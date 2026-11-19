package com.driveperf.iosexport

import io.sentry.kotlin.multiplatform.nsexception.setSentryUnhandledExceptionHook

/**
 * Kotlin/Native crash capture on iOS (ADR-007, DRIVE-196). An uncaught Kotlin exception ends the process through the
 * Kotlin runtime, so on its own Sentry Cocoa would only see an abort. The Sentry Kotlin Multiplatform hook reports the
 * Kotlin exception first, with its Kotlin stack trace, to the Sentry SDK the app has already started.
 *
 * The app starts and stops Sentry itself (Sentry Cocoa, after crash consent, `AppEnvironment.swift`), and calls [install]
 * right after starting it. Nothing here starts Sentry or sends anything.
 */
public object KotlinCrashHook {
    private var installed = false

    /** Installs the hook once per process. Call only after `SentrySDK.start`. */
    public fun install() {
        if (installed) return
        installed = true
        setSentryUnhandledExceptionHook()
    }
}
