package com.driveperf.platform

import android.os.UserManager
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.tripfile.TripFileStore
import com.driveperf.foundation.Clock
import com.driveperf.platform.activity.ActivityRecognitionSource
import com.driveperf.platform.capture.CaptureFailureBus
import com.driveperf.platform.capture.CaptureNotifications
import com.driveperf.platform.capture.CaptureSources
import com.driveperf.platform.capture.FileTripStore
import com.driveperf.platform.capture.InterimTripRecorder
import com.driveperf.platform.capture.TripIndex
import com.driveperf.platform.location.FusedLocationSource
import com.driveperf.platform.motion.SensorMotionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors

/**
 * Koin bindings for every Android adapter. The app's composition root includes this module (ADR-002 section 5). Each
 * adapter is bound to the shared port it implements, so DRIVE-62's `PlatformBindings` can take them as they are.
 */
val platformModule = module {
    single<Clock> { AndroidClock }
    single { CaptureFailureBus() }
    single<LocationSource> { FusedLocationSource(androidContext(), get(), Executors.newSingleThreadExecutor(), get()) }
    single<MotionSource> { SensorMotionSource(androidContext(), get(), get()) }
    single<ActivitySource> { ActivityRecognitionSource(androidContext(), get()) }
    single<TripFileStore> {
        val users = androidContext().getSystemService(UserManager::class.java)
        FileTripStore(File(androidContext().filesDir, "trips"), isUnlocked = users::isUserUnlocked)
    }
    single { TripIndex(File(androidContext().filesDir, "trips-index.tsv")) }
    single { CaptureNotifications(androidContext()) }
    single {
        InterimTripRecorder(
            sources = CaptureSources(get(), get(), get(), get()),
            clock = get(),
            store = get(),
            index = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
}
