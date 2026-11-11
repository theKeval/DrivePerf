package com.driveperf.di

import com.driveperf.data.local.IosDatabaseDriverFactory
import com.driveperf.pipeline.capture.CaptureConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Starts the core on iOS. Swift: `DrivePerfCore.shared.start(platform:)`, after Core Location sessions exist. */
public fun DrivePerfCore.start(platform: PlatformBindings): CoreServices =
    startWith(platform, IosDatabaseDriverFactory(), Dispatchers.IO)

/**
 * A throw-away core for the on-device self-test, on its own database [databaseName] and with a candidate timeout of
 * [candidateTimeoutS], so it never touches the user's trips. Swift deletes the database file and calls `close()` after.
 */
public fun DrivePerfCore.startScratch(platform: PlatformBindings, databaseName: String, candidateTimeoutS: Double): CoreServices =
    scratch(platform, IosDatabaseDriverFactory(databaseName), Dispatchers.IO, CaptureConfig(candidateTimeoutS = candidateTimeoutS))
