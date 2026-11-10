package com.driveperf.platform

import com.driveperf.platform.activity.ActivityTransitionSource
import com.driveperf.platform.capture.CaptureNotifications
import com.driveperf.platform.capture.FileTripFileStore
import com.driveperf.platform.capture.InterimTripRecorder
import com.driveperf.platform.capture.TripIndex
import com.driveperf.platform.location.FusedLocationSource
import com.driveperf.platform.motion.SensorMotionSource
import com.driveperf.platform.ports.ActivitySource
import com.driveperf.platform.ports.AndroidClock
import com.driveperf.platform.ports.Clock
import com.driveperf.platform.ports.LocationSource
import com.driveperf.platform.ports.MotionSource
import com.driveperf.platform.ports.TripFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors

/** Koin bindings for every Android adapter. The app's composition root includes this module (ADR-002 section 5). */
val platformModule = module {
    single<Clock> { AndroidClock }
    single<LocationSource> { FusedLocationSource(androidContext(), get(), Executors.newSingleThreadExecutor()) }
    single<MotionSource> { SensorMotionSource(androidContext()) }
    single<ActivitySource> { ActivityTransitionSource(androidContext()) }
    single<TripFileStore> { FileTripFileStore(File(androidContext().filesDir, "trips")) }
    single { TripIndex(File(androidContext().filesDir, "trips/index.tsv")) }
    single { CaptureNotifications(androidContext()) }
    single {
        InterimTripRecorder(
            location = get(),
            motion = get(),
            activity = get(),
            clock = get(),
            store = get(),
            index = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            dispatcher = Dispatchers.IO,
        )
    }
}
