package com.driveperf.di

import com.driveperf.domain.hello.GetHelloMessage
import com.driveperf.domain.hello.HelloMessage
import com.driveperf.foundation.platformDescription

/**
 * The single entry point apps use to reach the shared core (ADR-002 section 5). DRIVE-62 grows this into
 * `DrivePerfCore.start(platform: PlatformBindings)`; for now it only exposes the scaffold greeting.
 */
public object DrivePerfCore {
    /** The "Hello DrivePerf" screen content, computed by shared code on the current platform. */
    public fun hello(): HelloMessage = GetHelloMessage()(platformDescription())
}
