package com.driveperf.di

import android.content.Context
import com.driveperf.data.local.AndroidDatabaseDriverFactory
import kotlinx.coroutines.Dispatchers

/** Starts the core on Android. Call from `Application.onCreate` with the application context. */
public fun DrivePerfCore.start(platform: PlatformBindings, context: Context): CoreServices =
    startWith(platform, AndroidDatabaseDriverFactory(context.applicationContext), Dispatchers.IO)
