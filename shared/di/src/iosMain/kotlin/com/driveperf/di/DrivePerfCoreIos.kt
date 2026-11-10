package com.driveperf.di

import com.driveperf.data.local.IosDatabaseDriverFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Starts the core on iOS. Swift: `DrivePerfCore.shared.start(platform:)`, after Core Location sessions exist. */
public fun DrivePerfCore.start(platform: PlatformBindings): CoreServices =
    startWith(platform, IosDatabaseDriverFactory(), Dispatchers.IO)
