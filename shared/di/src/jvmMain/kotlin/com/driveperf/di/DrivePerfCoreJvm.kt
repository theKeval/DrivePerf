package com.driveperf.di

import com.driveperf.data.local.JvmDatabaseDriverFactory
import kotlinx.coroutines.Dispatchers

/** Starts the core on the JVM (tools and tests) on an SQLite database at [url], in memory by default. */
public fun DrivePerfCore.start(platform: PlatformBindings, url: String = "jdbc:sqlite:"): CoreServices =
    startWith(platform, JvmDatabaseDriverFactory(url), Dispatchers.IO)
